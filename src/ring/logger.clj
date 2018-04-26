(ns ring.logger
  "Ring middleware to log each request, response, and parameters."
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
  "Set of keys redacted by default"
  #{:authorization :password :token :secret :secret-key :secret-token})

(defn wrap-log-request-params
  "Ring middleware to log the parameters from each request

  Parameters are redacted by default, replacing the values that correspond to
  certain keys to \"[REDACTED]\". This is to prevent sensitive information from
  being written out to logs.

  Options may include:

    * log-fn: used to do the actual logging. Accepts a map with keys
              [level throwable message]. Defaults to `clojure.tools.logging/log`.
    * transform-fn: transforms a log item before it is passed through to log-fn. Messsage
              type it needs to handle: :params. It can filter messages by returning nil.
              Receives a map (a log item) with keys: [:level :throwable :message].
    * request-keys: Keys from the request that will be logged (unchanged) in addition to
              the data that ring.logger adds like [::type :params].
              Defaults to [:request-method :uri :server-name]
    * redact-key?: fn that is called on each key in the params map to check whether its
              value should be redacted. Receives the key, returns truthy/falsy. A common
              pattern is to use a set.
              Default value: #{:authorization :password :token :secret :secret-key :secret-token}"
  ([handler] (wrap-log-request-params handler {}))
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

(defn wrap-log-request-start
  "Ring middleware to log basic information about a request.

  Adds the key :ring.logger/start-ms to the request map

  Options may include:

    * log-fn: used to do the actual logging. Accepts a map with keys
              [level throwable message]. Defaults to `clojure.tools.logging/log`.
    * transform-fn: transforms a log item before it is passed through to log-fn. Messsage types
              it might need to handle: [:starting]. It can filter messages by returning nil.
              Receives a map (a log item) with keys: [:level :throwable :message].
    * request-keys: Keys from the request that will be logged (unchanged) in addition to the data
              that ring.logger adds like [::type ::ms :status].
              Defaults to [:request-method :uri :server-name]"
  ([handler] (wrap-log-request-start handler {}))
  ([handler {:keys [log-fn transform-fn request-keys]
             :or {log-fn default-log-fn
                  transform-fn identity
                  request-keys default-request-keys}}]
   (fn [request]
     (let [start-ms (System/currentTimeMillis)
           log (make-transform-and-log-fn transform-fn log-fn)]
       (log {:level :info
             :message (-> (select-keys request request-keys)
                          (assoc ::type :starting))})
       (-> (assoc request ::start-ms start-ms)
           handler)))))

(defn wrap-log-response
  "Ring middleware to log response and timing for each request.

  Takes the starting timestamp (in msec.) from the :ring.logger/start-ms key in
  the request map, or System/currentTimeMillis if that key is not present.

  Options may include:

    * log-fn: used to do the actual logging. Accepts a map with keys
              [level throwable message]. Defaults to `clojure.tools.logging/log`.
    * transform-fn: transforms a log item before it is passed through to log-fn. Messsage types
              it might need to handle: [:finish :exception]. It can filter messages by
              returning nil. Receives a map (a log item) with keys: [:level :throwable :message].
    * request-keys: Keys from the request that will be logged (unchanged) in addition to the data
              that ring.logger adds like [::type ::ms :status].
              Defaults to [:request-method :uri :server-name]
    * log-exceptions?: When true, logs exceptions as an :error level message, rethrowing
              the original exception. Defaults to true"
  ([handler] (wrap-log-response handler {}))
  ([handler {:keys [log-fn log-exceptions? transform-fn request-keys]
             :or {log-fn default-log-fn
                  transform-fn identity
                  log-exceptions? true
                  request-keys default-request-keys}}]
   (fn [request]
     (let [start-ms (or (::start-ms request)
                        (System/currentTimeMillis))
           log (make-transform-and-log-fn transform-fn log-fn)
           base-message (select-keys request request-keys)]
       (try
         (let [{:keys [status] :as response} (handler request)
               elapsed-ms (- (System/currentTimeMillis) start-ms)
               level (if (and (number? status)
                              (<= 500 status))
                       :error
                       :info)]
           (log {:level level
                 :message (-> base-message
                              (assoc ::type :finish
                                     :status status
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
  "Returns a ring middleware handler to log arrival, response, and parameters
  for each request.

  Log messages are simple clojure maps that can be transformed to a different
  representation (string, JSON, etc.) via the transform-fn option

  Options may include:

    * log-fn: used to do the actual logging. Accepts a map with keys
              [level throwable message]. Defaults to `clojure.tools.logging/log`.
    * transform-fn: transforms a log item before it is passed through to log-fn. Messsage types
              it might need to handle: [:starting :params :finish :exception]. It can filter
              log items by returning nil. Log items are maps with keys: [:level :throwable :message].
    * request-keys: Keys from the request that will be logged (unchanged) in addition to the data
              that ring.logger adds like [::type ::ms].
              Defaults to [:request-method :uri :server-name]
    * log-exceptions?: When true, logs exceptions as an :error level message, rethrowing
              the original exception. Defaults to true
    * redact-key?: fn that is called on each key in the params map to check whether its
              value should be redacted. Receives the key, returns truthy/falsy. A common
              pattern is to use a set.
              Default value: #{:authorization :password :token :secret :secret-key :secret-token}"
  ([handler options]
   (-> handler
       (wrap-log-response options)
       (wrap-log-request-params options)
       (wrap-log-request-start options)))
  ([handler]
   (wrap-with-logger handler {})))
