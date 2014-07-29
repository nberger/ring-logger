(ns ring.middleware.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
   [org.tobereplaced (mapply :refer [mapply])]
   [clojure.java.io]
   [onelog.core :as log]
   [clj-logging-config.log4j :as log-config]
   [clansi.core :as ansi]))



;; TODO: Alter this subsystem to contain a predefined map of all
;; acceptable fg/bg combinations, since some (e.g. white on yellow)
;; are practically illegible.
(def id-colorizations
  "Foreground / background color codes allowable for random ID colorization."
  {:white :bg-white :black :bg-black :red :bg-red :green :bg-green :blue :bg-blue :yellow :bg-yellow :magenta :bg-magenta :cyan :bg-cyan} )

(def id-foreground-colors (keys id-colorizations))
(def id-colorization-count (count id-foreground-colors))


(defn- get-colorization
  [id]
  "Returns a consistent colorization for the given id; that is, the
  same ID produces the same color pattern. The colorization will have
  distinct foreground and background colors."
  (let [foreground (nth id-foreground-colors (mod id id-colorization-count))
        background-possibilities (vals (dissoc id-colorizations foreground))
        background (nth background-possibilities (mod id (- id-colorization-count 1)))]
    [foreground background]))


(defn- format-id [id]
  "Returns a standard colorized, printable representation of a request id."
  (if id
    (apply ansi/style (format "%04x" id) [:bright] (get-colorization id))))


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
                                                       :conent-type :content-length :character-encoding])))
  (if params
    (info (str "  \\ - - - -  Params: " params))))


(defn- post-logger
  "Logs data about a finished request.

Sends all log messages at \"info\" level to the logging
infrastructure, unless status is >= 500, in which case they are sent as errors."

  [{:keys [error info] :as options}
   {:keys [request-method uri remote-addr query-string] :as req}
   {:keys [status] :as resp}  
   totaltime]

  (log/trace "[ring] Sending response: " resp)

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
  [{:keys [error] :as options}
   {:keys [request-method uri remote-addr] :as request}
   throwable totaltime]
  (error (str (ansi/style "Uncaught exception processing request:" :bright :red)  " for " remote-addr " in (" totaltime " ms) - request was: " request))
  (error (log/throwable throwable)))


(defn- make-logger-middleware
  [handler & {:keys [pre-logger post-logger exception-logger] :as options}]
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
    (let [start (System/currentTimeMillis)]
      (log-config/with-logging-context (format-id (rand-int 0xffff))
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
            (throw t)))))))


(def default-options
  "Default logging functions."
  {:info  (fn [x] (log/info x))
   :debug (fn [x] (log/debug x))
   :error (fn [x] (log/error x))
   :warn  (fn [x] (log/warn x))
   :pre-logger pre-logger
   :post-logger post-logger
   :exception-logger exception-logger
   })


(defn wrap-with-logger
  "Returns a Ring middleware handler which uses the prepackaged color loggers.

   Options may include the :info, :debug, and :error keys.
   Values are functions that accept a string argument and log it at that level.
   Uses OneLog to log if none are supplied."
  ([handler & {:as options}]
     (mapply (partial make-logger-middleware handler)
                             (merge default-options
                                    options))))



(defn wrap-with-body-logger
  "Returns a Ring middleware handler that will log the bodies of any
  incoming requests by reading them into memory, logging them, and
  then putting them back into a new InputStream for other handlers to
  read. 

  This is inefficient, and should only be used for debugging."
  [handler logger-fns]
  (fn [request]
    (let [body (slurp (:body request))]
      ((:info logger-fns)  " -- Raw request body: '" body "'")
      (handler (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body)))))))
