(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
   [clojure.java.io]
   [ring.logger.tools-logging :refer [make-tools-logging-logger]]
   [ring.logger.messages :as messages]
   [ring.logger.protocols :refer [Logger error info warn debug trace add-extra-middleware]]))

(defn- wrap-with-logger*
  [handler options]
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

(defn wrap-request-start [handler]
  #(-> %
       (assoc :logger-start-time (System/currentTimeMillis))
       handler))

(defn wrap-with-logger
  "
  Returns a Ring middleware handler that logs request and response details.

  Options may include:
    * logger: Reifies ring.logger.protocoles/Logger. If not provided will use
              a tools.logging logger.
    * printer: Used for dispatching to the messages multimethods. If not present
               it will use the default implementation which adds ANSI coloring to
               the messages. A :no-color printer is provided.

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
  "
  ([handler {:keys [logger] :as options}]
   (let [logger (or logger (make-tools-logging-logger))
         options (merge {:logger logger}
                        options)]
     (-> handler
         (wrap-with-logger* options)
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
