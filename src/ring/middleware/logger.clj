(ns ring.middleware.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
   [clojure.java.io]
   [ring.middleware.logger.tools-logging :refer [make-tools-logging-logger]]
   [ring.middleware.logger.protocols :refer [Logger error error-with-ex info warn debug trace add-extra-middleware]]
   [clansi.core :as ansi]))


(defn- pre-logger
  [{:keys [info debug] :as options}
   {:keys [request-method uri remote-addr query-string params] :as req}]
  (info (str (ansi/style "Starting " :cyan)
             request-method " " 
             uri (if query-string (str "?" query-string)) 
             " for " remote-addr
             " " (dissoc (:headers req) "authorization"))) ;; log headers, but don't log username/password, if any

  (debug (str "Request details: " (select-keys req [:server-port :server-name :remote-addr :uri 
                                                       :query-string :scheme :request-method 
                                                       :content-type :content-length :character-encoding])))
  (if params
    (info (str "  \\ - - - -  Params: " params))))


(defn- post-logger
  "Logs data about a finished request.

Sends all log messages at \"info\" level to the logging
infrastructure, unless status is >= 500, in which case they are sent as errors."

  [{:keys [error info trace] :as options}
   {:keys [request-method uri remote-addr query-string] :as req}
   {:keys [status] :as resp}  
   totaltime]

  (trace (str "[ring] Sending response: " resp))

  (let [colortime (try (apply ansi/style
                              (str totaltime)
                              (cond
                               (>= totaltime 1500)  [:bright :red]
                               (>= totaltime 800)   [:red]
                               (>= totaltime 500)   [:yellow]
                               :else :default))
                       (catch Exception e (or totaltime "??")))
        
        colorstatus (try (apply ansi/style
                                (str status)
                                (cond
                                 (< status 300)  [:default] 
                                 (>= status 500) [:bright :red] 
                                 (>= status 400) [:red] 
                                 :else           [:yellow]))
                         (catch Exception e (or status "???")))
        log-message (str (ansi/style "Finished " :cyan)
                         request-method " " 
                         uri  (if query-string (str "?" query-string))
                         " for " remote-addr
                         " in (" colortime " ms)"
                         " Status: " colorstatus

                         (when (= status 302)
                           (str " redirect to " (get-in resp [:headers "Location"])))

                         ) ]

    (if (and (number? status) (>= status 500))
      (error log-message)
      (info  log-message))))


(defn- exception-logger
  [{:keys [error error-with-ex] :as options}
   {:keys [request-method uri remote-addr] :as request}
   throwable totaltime]
  (error (str (ansi/style "Uncaught exception processing request:" :bright :red)  " for " remote-addr " in (" totaltime " ms) - request was: " request))
  (error-with-ex throwable ""))


(defn- make-logger-middleware
  [handler {:keys [pre-logger post-logger exception-logger] :as options}]
  "Adds logging for requests using the given logger functions.

The convenience function (wrap-with-logger) calls this function with
default loggers. Use (make-logger) directly if you want to supply your own
logger functions.

Each request is assigned a random hex id, to allow logged events
relevant to a particular request to be correlated. This is exposed as
a log4j Nested Diagnostic Context (NDC). Add a %x to your log format
string to log it. (OneLog already includes the necessary %x by
default.)

The pre-logger function is called before the handler is invoked, and
receives the request as an argument.

The post-logger function is called after the response is generated,
and receives the request, the response, and the total time taken by the handler as arguments

The exception-logger function is called in a (catch) clause, if an
exception is thrown during the handler's run. It receives the request,
the Throwable that was thrown, and the total time taken to that
point. It re-throws the exception after logging it, so that other
middleware has a chance to do something with it.
"
;; Long ago, originally based on
;; https://gist.github.com/kognate/noir.incubator/blob/master/src/noir.incubator/middleware.clj
  (fn [request]
    (let [start (:logger-start-time request)]
      (try
        (pre-logger options
                    request)

        (let [response (handler request)
              finish (System/currentTimeMillis)
              total  (- finish start)]

          (post-logger options
                       request
                       response
                       total)

          response)

        (catch Throwable t
          (let [finish (System/currentTimeMillis)
                total  (- finish start)]
            (exception-logger options
                              request
                              t
                              total))
          (throw t))))))


(defn make-default-options
  "Default logging functions."
  [logger-impl]
  (let [logger-impl (or logger-impl (make-tools-logging-logger))]
    {:logger-impl logger-impl
     :info  #(info logger-impl %)
     :debug #(debug logger-impl %)
     :error #(error logger-impl %)
     :error-with-ex #(error-with-ex logger-impl %1 %2)
     :warn  #(warn logger-impl %)
     :trace #(trace logger-impl %)
     :pre-logger pre-logger
     :post-logger post-logger
     :exception-logger exception-logger}))

(defn wrap-request-start [handler]
  (fn [request]
    (let [now (System/currentTimeMillis)]
      (->> now
           (assoc request :logger-start-time)
           handler))))

(defn wrap-with-logger
  "Returns a Ring middleware handler which uses the prepackaged color loggers.

   Options may include the :logger-impl, :info, :debug, :trace and :error keys.
   Values are functions that accept a string argument and log it at that level.
   Uses OneLog to log if none are supplied."
  ([handler & {:keys [logger-impl] :as options}]
   (let [options (merge (make-default-options logger-impl)
                        options)
         logger-impl (:logger-impl options)]
     (-> handler
         (make-logger-middleware options)
         (#(add-extra-middleware logger-impl %))
         wrap-request-start))))



(defn wrap-with-body-logger
  "Returns a Ring middleware handler that will log the bodies of any
  incoming requests by reading them into memory, logging them, and
  then putting them back into a new InputStream for other handlers to
  read. 

  This is inefficient, and should only be used for debugging."
  [handler logger-fns]
  (fn [request]
    (let [body ^String (slurp (:body request))]
      ((:info logger-fns)  " -- Raw request body: '" body "'")
      (handler (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body)))))))
