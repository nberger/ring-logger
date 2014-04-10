(ns ring.middleware.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (require
   [clojure.java.io]
   [clojure.tools.logging]
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
  (apply ansi/style (format "%04x" id) [:bright] (get-colorization id)))


(defn- pre-logger
  [logger-fns id {:keys [request-method uri remote-addr query-string params] :as req}]
  ((:info logger-fns) (str "[" id "] Starting " request-method " " uri (if query-string (str "?" query-string)) " for " remote-addr
                           " - " (dissoc (:headers req) "authorization"))) ;; don't log username/password, if any

  ((:debug logger-fns) (str "[" id "] Request details: " (select-keys req [:server-port :server-name :remote-addr :uri 
                                                                           :query-string :scheme :request-method 
                                                                           :conent-type :content-length :character-encoding])))
  (if params
    ((:info logger-fns) (str "[" id "]  \\ - - - -  Params: " params))))


(defn- colorless-pre-logger
  "Like pre-logger, but doesn't log any ANSI color codes."
  [id request]

  (ansi/without-ansi (pre-logger id request)))


(defn- post-logger
  "Logs data about a finished request.

Log messages will be colorized with ANSI escape codes. Use
colorless-logger if you want plaintext.

The id is randomly colorized according to its value, to make it easier
to visually correlate request/response/exception log sets.

Sends all log messages at \"info\" level to the logging
infrastructure, unless status is >= 500, in which case they are sent as errors."

  [logger-fns id {:keys [request-method uri remote-addr query-string] :as req}  {:keys [status] :as resp}  totaltime]
  (let [colortime (try (apply ansi/style
                              (str totaltime)
                              (cond
                               (>= totaltime 1500)  [:bright :red]
                               (>= totaltime 1000) [:red]
                               (>= totaltime 500) [:yellow]
                               :else :default))
                       (catch Exception e (or totaltime "??")))
        
        colorstatus (try (apply ansi/style
                                (str status)
                                (cond
                                 (< status 300) [:default] 
                                 (>= status 500) [:bright :red] 
                                 (>= status 400) [:red] 
                                 :else [:yellow]))
                         (catch Exception e (or status "???")))
        log-message (str
                     "[" id "] "
                     "Finished " request-method " " uri  (if query-string (str "?" query-string)) " for " remote-addr " in (" colortime " ms)"
                     " Status: " colorstatus
                     ) ]

    (if (and (number? status) (>= status 500))
      ((:error logger-fns) log-message)
      ((:info logger-fns)  log-message))))


(defn- colorless-post-logger
  [id request response totaltime]
  "Like post-logger, but doesn't log any ANSI color codes."
  (ansi/without-ansi (post-logger id request response totaltime)))


(defn- exception-logger
  [id logger-fns {:keys [request-method uri remote-addr] :as request} throwable totaltime]
  ((:error logger-fns) (str "[" id "] " (ansi/style "Exception!" :bright :red)  " for " remote-addr " in (" totaltime " ms)"))
  ((:error logger-fns) throwable (str "- End stacktrace for " id "-"))) ;; must include a second string argument to make c.t.logging recognize first arg as an exception)


(defn- colorless-exception-logger
  [id request throwable totaltime]
  "Like pre-logger, but doesn't log any ANSI color codes."
  (ansi/without-ansi (exception-logger id request throwable totaltime)))

;; To make it easy to find this inside the handler for your own
;; logging, this is a dynamic variable.
(def ^:dynamic *request-id* nil)

(defn- make-logger-middleware
  [handler logger-fns pre-logger post-logger exception-logger]
  "Adds logging for requests using the given logger functions.

The convenience function (wrap-with-logger) calls this function with
default loggers. Use (make-logger) directly if you want to supply your own
logger functions.

Each request is assigned a random hex id, to allow the 3 possible
logging events to be correlated.

The pre-logger function is called before the handler is invoked, and
receives the id and the request as an argument.

The post-logger function is called after the response is generated,
and receives four arguments: the id, the request, the response, and the
total time taken by the handler.

The exception-logger function is called in a (catch) clause, if an
exception is thrown during the handler's run. It receives four
arguments: the id, the request, the Throwable that was thrown, and the
total time taken to that point. It re-throws the exception after
logging it, so that other middleware has a chance to do something with
it.
"
;; originally based on
;; https://gist.github.com/kognate/noir.incubator/blob/master/src/noir.incubator/middleware.clj
  (fn [request]
    (let [start (System/currentTimeMillis)]
      (binding [*request-id* (format-id (rand-int 0xffff))]
        (try
          (pre-logger logger-fns *request-id* request)
          (let [response (handler request)
                finish (System/currentTimeMillis)
                total  (- finish start)]
            (post-logger logger-fns *request-id* request response total)
            response)
          (catch Throwable t
            (let [finish (System/currentTimeMillis)
                  total  (- finish start)]
              (exception-logger logger-fns *request-id* request t total))
            (throw t)))))))


(def ctl-logging-fns
  "Default logging functions that use clojure.tools.logging."
  {:info  (fn [x] (clojure.tools.logging/info x))
   :debug (fn [x] (clojure.tools.logging/debug x))
   :error (fn [x] (clojure.tools.logging/error x))
   :warn  (fn [x] (clojure.tools.logging/warn x))
   })


(defn wrap-with-logger
  "Returns a Ring middleware handler which uses the prepackaged color loggers.

   logger-fns is a map that must include the :info, :debug, and :error keys. 
   Values are functions that accept a string argument and log it at that level.
   If it is not given, uses the ones from clojure.tools.logging."
  ([handler logger-fns]
     (make-logger-middleware handler logger-fns pre-logger post-logger exception-logger))

  ([handler] (wrap-with-logger handler ctl-logging-fns)))


(defn wrap-with-plaintext-logger
  "Returns a Ring middleware handler which uses the ANSI-colorless prepackaged loggers.

   suppress-log-initialization prevents the creation of superfluous stub
   logfiles in environments where some other code has already
   initialized the logger, such as in an Immutant container."
  ([handler logger-fns]
     (make-logger-middleware handler logger-fns colorless-pre-logger colorless-post-logger colorless-exception-logger))

  ([handler] (wrap-with-plaintext-logger handler ctl-logging-fns)))


(defn wrap-with-body-logger
  "Returns a Ring middleware handler that will log the bodies of any
  incoming requests by reading them into memory, logging them, and
  then putting them back into a new InputStream for other handlers to
  read. 

  This is inefficient, and should only be used for debugging.

  TODO: Add request ID."
  [handler logger-fns]
  (fn [request]
    (let [body (slurp (:body request))]
      ((:info logger-fns)  " -- Raw request body: '" body "'")
      (handler (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body)))))))
