(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
    [clojure.tools.logging :as c.t.logging]))

(defn default-request-id-fn [_]
  (rand-int 0xffffff))

(defn default-log-fn [level throwable message]
  (c.t.logging/log level throwable message))

(defn wrap-log-params
  ([handler] (wrap-log-params {}))
  ([handler {:keys [log-fn log-level]
             :or {log-fn default-log-fn
                  log-level :debug}}]
   (fn [{:keys [::request-id] :as request}]
     (log-fn log-level nil (-> (select-keys request [:request-method :uri])
                                (assoc ::type :params
                                     :params (:params request))
                                (cond-> request-id (assoc ::request-id request-id))))
     (handler request))))

(defn wrap-log-request
  ([handler] (wrap-log-request handler {}))
  ([handler {:keys [request-id-fn log-fn log-exceptions?]
             :or {request-id-fn default-request-id-fn
                  log-fn default-log-fn
                  log-exceptions? true}}]
   (fn [request]
     (let [start-ms (System/currentTimeMillis)
           request-id (request-id-fn request)]
       (log-fn :info nil (-> (select-keys request [:request-method :uri])
                             (assoc ::type :starting)
                             (cond-> request-id (assoc ::request-id request-id))))
       (try
         (let [response (-> request
                            (cond-> request-id (assoc ::request-id request-id))
                            (handler))
               elapsed-ms (- (System/currentTimeMillis) start-ms)]
           (log-fn :info nil (-> (select-keys request [:request-method :uri])
                                 (assoc ::type :finish
                                        :status (:status response)
                                        ::ms elapsed-ms)
                                 (cond-> request-id (assoc ::request-id request-id))))
           response)
         (catch Exception e
           (when log-exceptions?
             (let [elapsed-ms (- (System/currentTimeMillis) start-ms)]
               (log-fn :error e (-> (select-keys request [:request-method :uri])
                                    (assoc ::type :exception
                                           ::ms elapsed-ms)
                                    (cond-> request-id (assoc ::request-id request-id))))))
           (throw e)))))))

(defn wrap-with-logger
  "Returns a ring middleware handler that logs request and response details.

  Options may include:
    * log-fn: used to do the actual logging. Accepts 3 params
              [level throwable message]. Defaults to `clojure.tools.logging/log`.
    * request-id-fn: takes a request and returns a unique request id which is logged
              and added to the request map under :ring.logger/request-id key.
              The key is not added when the fn returns nil. Defaults
              to `ring.logger/default-request-id-fn`.
    * log-exceptions?: When true, logs exceptions as an :error level message, rethrowing
              the original exception. Defaults to true"
  ([handler options]
   (-> handler
       (wrap-log-params options)
       (wrap-log-request options)))
  ([handler]
   (wrap-with-logger handler {})))
