(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
   [clojure.tools.logging :as c.t.logging]
   [ring.logger.redaction :as redaction]))

(defn default-log-fn [{:keys [level throwable message]}]
  (c.t.logging/log level throwable message))

(defn make-transform-and-log-fn [transform-fn log-fn]
  (fn [message]
    (when-let [transformed (transform-fn message)]
      (log-fn transformed))))

(def default-request-keys
  [:request-method :uri :server-name])

(def default-redact-key?
  "A set of the keys redacted by default"
  #{:authorization :password :token :secret :secret-key :secret-token})

(defn wrap-log-params
  ([handler] (wrap-log-params {}))
  ([handler {:keys [log-fn log-level transform-fn request-keys redact-key? redact-value-fn]
             :or {log-fn default-log-fn
                  transform-fn identity
                  log-level :debug
                  request-keys default-request-keys
                  redact-key? default-redact-key?
                  redact-value-fn (constantly "[REDACTED]")}}]
   (fn [request]
     (let [log (make-transform-and-log-fn transform-fn log-fn)
           redacted-params (redaction/redact-map (:params request)
                                                 {:redact-key? redact-key?
                                                  :redact-value-fn redact-value-fn})]
       (log {:level log-level
             :message (-> (select-keys request request-keys)
                          (assoc ::type :params
                                 :params redacted-params))}))
     (handler request))))

(defn wrap-log-request
  ([handler] (wrap-log-request handler {}))
  ([handler {:keys [log-fn log-exceptions? transform-fn request-keys]
             :or {log-fn default-log-fn
                  transform-fn identity
                  log-exceptions? true
                  request-keys default-request-keys}}]
   (fn [request]
     (let [start-ms (System/currentTimeMillis)
           log (make-transform-and-log-fn transform-fn log-fn)
           base-message (select-keys request request-keys)]
       (log {:level :info
             :message (-> base-message
                          (assoc ::type :starting))})
       (try
         (let [response (handler request)
               elapsed-ms (- (System/currentTimeMillis) start-ms)]
           (log {:level :info
                 :message (-> base-message
                              (assoc ::type :finish
                                     :status (:status response)
                                     ::ms elapsed-ms))})
           response)
         (catch Exception e
           (when log-exceptions?
             (let [elapsed-ms (- (System/currentTimeMillis) start-ms)]
               (log {:level :error
                     :throwable e
                     :message (-> base-message
                                  (assoc ::type :exception
                                         ::ms elapsed-ms))})))
           (throw e)))))))

(defn wrap-with-logger
  "Returns a ring middleware handler that logs request and response details.

  Options may include:
    * log-fn: used to do the actual logging. Accepts a map with keys
              [level throwable message]. Defaults to `clojure.tools.logging/log`.
    * transform-fn: transforms a log item before it is passed through to log-fn. Messsage types
              it might need to handle: [:start :params :finish :exception]. It can be
              filter messages by returning nil. Log items are maps with keys:
              [:level :throwable :message].
    * request-keys: Keys from the request that will be logged (unchanged) in addition to the data
              that ring.logger adds like [::type ::ms].
              Defaults to [:request-method :uri :server-name]
    * log-exceptions?: When true, logs exceptions as an :error level message, rethrowing
              the original exception. Defaults to true"
  ([handler options]
   (-> handler
       (wrap-log-params options)
       (wrap-log-request options)))
  ([handler]
   (wrap-with-logger handler {})))
