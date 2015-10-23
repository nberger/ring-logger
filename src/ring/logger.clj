(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
    [ring.logger.tools-logging :refer [make-tools-logging-logger]]
    [ring.logger.messages :as messages]
    [ring.logger.protocols :refer [add-extra-middleware debug]]))

(defn- wrap-with-logger*
  [handler {:keys [timing exceptions] :as options}]
;; Long ago, originally based on
;; https://gist.github.com/kognate/noir.incubator/blob/master/src/noir.incubator/middleware.clj
  (fn [request]
    (try
      (messages/starting options request)
      (messages/request-details options request)
      (messages/request-params options request)

      (let [response (handler request)
            request (if timing
                      (assoc request :logger-end-time (System/currentTimeMillis))
                      request)]
        (messages/sending-response options response)
        (messages/finished options request response)

        response)

      (catch Throwable t
        (when exceptions
          (let [request (if timing
                          (assoc request :logger-end-time (System/currentTimeMillis))
                          request)]
            (messages/exception options request t)))
        (throw t)))))

(defn wrap-request-start [handler]
  #(-> %
       (assoc :logger-start-time (System/currentTimeMillis))
       handler))

(defn make-options [options]
  (let [logger (or (:logger options) (make-tools-logging-logger))
        redact-keys (or (:redact-keys options) #{:authorization :password})
        redact-value (or (:redact-value options) "[REDACTED]")
        redact-fn (or (:redact-fn options) (messages/redact-some redact-keys (constantly redact-value)))]
    (merge {:logger logger
            :redact-fn redact-fn
            :exceptions true
            :timing true}
           options)))

(defn wrap-with-logger
  "
  Returns a Ring middleware handler that logs request and response details.

  Options may include:
    * logger: Reifies ring.logger.protocoles/Logger. If not provided will use
              a tools.logging logger.
    * printer: Used for dispatching to the messages multimethods. If not present
               it will use the default implementation which adds ANSI coloring to
               the messages. A :no-color printer is provided.
    * timing: Log the time taken by the app handler? Defaults to true.
    * exceptions: Catch, log & rethrow exceptions. Defaults to true
    * redact-fn: Function used to redact headers and params.
                 Default: logger.messages/redact-some built from :redact-keys &
                 :redact-value options
    * redact-keys: Key set passed to build the default redact-fn. Ignored if :redact-fn
                   is present. Default: #{:authorization :password}
    * redact-value: Value used as the replacement for redacted keys. It's passed to build
                    the default redact-fn as `(constantly redact-value)`

  The actual logging is done by the multimethods in the messages ns.

  Before the handler is executed:
    * messages/starting
    * messages/request-details
    * messages/request-params

  After the handler was executed:
    * messages/sending-response
    * messages/finished

  When an exception occurs (and :exceptions option is not false):
    * messages/exception
  "
  ([handler options]
   (let [{:keys [logger timing] :as options} (make-options options)]
     (cond-> handler
         :always (wrap-with-logger* options)
         :always (#(add-extra-middleware logger %))
         timing wrap-request-start)))
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
      (debug logger (str "-- Raw request body: '" body "'"))
      (handler (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body)))))))
  ([handler]
   (wrap-with-body-logger handler (make-tools-logging-logger))))
