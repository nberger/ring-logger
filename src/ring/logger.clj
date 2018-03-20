(ns ring.logger
  "Ring middleware that logs information about each request to a given
  set of generic logging functions."
  (:require
    [clojure.tools.logging :as c.t.logging]))

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
   (fn [request]
     (let [log (make-transform-and-log-fn transform-fn log-fn)]
       (log {:level log-level
             :message (-> (select-keys request [:request-method :uri :server-name])
                          (assoc ::type :params
                                 :params (:params request)))}))
     (handler request))))

(defn wrap-log-request
  ([handler] (wrap-log-request handler {}))
  ([handler {:keys [log-fn log-exceptions? transform-fn]
             :or {log-fn default-log-fn
                  transform-fn identity
                  log-exceptions? true}}]
   (fn [request]
     (let [start-ms (System/currentTimeMillis)
           log (make-transform-and-log-fn transform-fn log-fn)]
       (log {:level :info
             :message (-> (select-keys request [:request-method :uri :server-name])
                          (assoc ::type :starting))})
       (try
         (let [response (handler request)
               elapsed-ms (- (System/currentTimeMillis) start-ms)]
           (log {:level :info
                 :message (-> (select-keys request [:request-method :uri :server-name])
                              (assoc ::type :finish
                                     :status (:status response)
                                     ::ms elapsed-ms))})
           response)
         (catch Exception e
           (when log-exceptions?
             (let [elapsed-ms (- (System/currentTimeMillis) start-ms)]
               (log {:level :error
                     :throwable e
                     :message (-> (select-keys request [:request-method :uri :server-name])
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
    * log-exceptions?: When true, logs exceptions as an :error level message, rethrowing
              the original exception. Defaults to true"
  ([handler options]
   (-> handler
       (wrap-log-params options)
       (wrap-log-request options)))
  ([handler]
   (wrap-with-logger handler {})))
