(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
   [clojure.java.io]
   [ring.logger.tools-logging :refer [make-tools-logging-logger]]
   [ring.logger.messages :as messages]
   [ring.logger.protocols :refer [Logger error info warn debug trace add-extra-middleware]]))

(defn- make-logger-middleware
  [handler options]
  "Adds logging for requests using the given logger.

The actual logging is done by the multimethods in the messages ns.

Before the handler is executed:
  * messages/starting
  * messages/request-details
  * messages/request-params

After the handler was executed:
  * messages/sending-response
  * messages/finished

When an exception occurs:
  * messages/exception

(with ring-logger-onelog): Each request is assigned a random hex id,
to allow logged events relevant to a particular request to be correlated.
This is exposed as a log4j Nested Diagnostic Context (NDC). Add a %x to your
log format string to log it. (OneLog already includes the necessary %x by
default.)
"
;; Long ago, originally based on
;; https://gist.github.com/kognate/noir.incubator/blob/master/src/noir.incubator/middleware.clj
  (fn [request]
    (let [start (:logger-start-time request)]
      (try
        (messages/starting options request)
        (messages/request-details options request)
        (messages/request-params options request)

        (let [response (handler request)
              total  (- (System/currentTimeMillis) start)]
          (messages/sending-response options response)
          (messages/finished options request response total)

          response)

        (catch Throwable t
          (let [total (- (System/currentTimeMillis) start)]
            (messages/exception options request t total))
          (throw t))))))


(defn make-default-options
  "Default logging functions."
  [logger]
  (let [logger (or logger (make-tools-logging-logger))]
    {:logger logger}))

(defn wrap-request-start [handler]
  #(-> %
       (assoc :logger-start-time (System/currentTimeMillis))
       handler))

(defn wrap-with-logger
  "Returns a Ring middleware handler which uses the prepackaged color loggers.

   Options may include :logger, :info, :debug, :trace, :error, :warn & :printer.
   Values are functions that accept a string argument and log it at that level.
   Uses tools.logging to log if none are supplied."
  ([handler {:keys [logger] :as options}]
   (let [options (merge (make-default-options logger)
                        options)
         logger (:logger options)]
     (-> handler
         (make-logger-middleware options)
         (#(add-extra-middleware logger %))
         wrap-request-start)))
  ([handler]
   (wrap-with-logger handler {})))

(defn wrap-with-body-logger
  "Returns a Ring middleware handler that will log the bodies of any
  incoming requests by reading them into memory, logging them, and
  then putting them back into a new InputStream for other handlers to
  read.

  This is inefficient, and should only be used for debugging."
  ([handler logger]
  (fn [request]
    (let [body ^String (slurp (:body request))]
      (debug logger  " -- Raw request body: '" body "'")
      (handler (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body)))))))
  ([handler]
   (wrap-with-body-logger handler (make-tools-logging-logger))))
