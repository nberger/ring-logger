(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
    [clojure.tools.logging :as c.t.logging]))

(defn default-request-id-fn [_]
  (rand-int 0xffffff))

(defn default-log-fn [{:keys [level throwable message]}]
  (c.t.logging/log level throwable message))

(defn make-transform-and-log-fn [transform-fn log-fn]
  (fn [message]
    (when-let [transformed (transform-fn message)]
      (log-fn transformed))))

(defn wrap-log-params
  ([handler] (wrap-log-params {}))
  ([handler {:keys [log-fn log-level transform-fn]
             :or {log-fn default-log-fn
                  transform-fn identity
                  log-level :debug}}]
   (fn [{:keys [::request-id] :as request}]
     (let [log (make-transform-and-log-fn transform-fn log-fn)]
       (log {:level log-level
            :message (-> (select-keys request [:request-method :uri :server-name])
                         (assoc ::type :params
                                :params (:params request))
                         (cond-> request-id (assoc ::request-id request-id)))}))
     (handler request))))

(defn wrap-log-request
  ([handler] (wrap-log-request handler {}))
  ([handler {:keys [request-id-fn log-fn log-exceptions? transform-fn]
             :or {request-id-fn default-request-id-fn
                  log-fn default-log-fn
                  transform-fn identity
                  log-exceptions? true}}]
   (fn [request]
     (let [start-ms (System/currentTimeMillis)
           request-id (request-id-fn request)
           log (make-transform-and-log-fn transform-fn log-fn)]
       (log {:level :info
             :message (-> (select-keys request [:request-method :uri :server-name])
                          (assoc ::type :starting)
                          (cond-> request-id (assoc ::request-id request-id)))})
       (try
         (let [response (-> request
                            (cond-> request-id (assoc ::request-id request-id))
                            (handler))
               elapsed-ms (- (System/currentTimeMillis) start-ms)]
           (log {:level :info
                 :message (-> (select-keys request [:request-method :uri :server-name])
                              (assoc ::type :finish
                                     :status (:status response)
                                     ::ms elapsed-ms)
                              (cond-> request-id (assoc ::request-id request-id)))})
           response)
         (catch Exception e
           (when log-exceptions?
             (let [elapsed-ms (- (System/currentTimeMillis) start-ms)]
               (log {:level :error
                     :throwable e
                     :message (-> (select-keys request [:request-method :uri :server-name])
                                  (assoc ::type :exception
                                         ::ms elapsed-ms)
                                  (cond-> request-id (assoc ::request-id request-id)))})))
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
