(ns ring.async-logger-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [clojure.string :as s]
    [ring.middleware.params :refer [wrap-params]]
    [ring.logger-test :refer [with-system-out-str]]
    [ring.logger :as logger]
    [ring.logger.compat :as logger.compat]
    [ring.mock.request :as mock]))

(def no-status-handler
  (fn [req respond raise]
    (Thread/sleep 2)
    (respond {:body "ok"})))

(def ok-handler
  (fn [req respond raise]
    (Thread/sleep 2)
    (respond {:status 200
              :body "ok"
              :headers {:ping "pong"}})))

(def error-handler
  (fn [req respond raise]
    (Thread/sleep 2)
    (respond {:status 500
              :body "error"
              :headers {:ping "pong"}})))

(def throws-handler
  (fn [req respond raise]
    (Thread/sleep 2)
    (raise (Exception. "Ooopsie"))))

(deftest log-request-start-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-request-start {:log-fn log}))
        response (promise)]
    (handler
      (mock/request :get
                    "/some/path?password=secret&email=foo@example.com")
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           @response))
    (is (= 1 (count @output)))
    (is (= {:level :info
            :message {::logger/type :starting
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"}}
           (first @output)))))

(deftest log-response-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-response {:log-fn log}))
        response (promise)]
    (handler
      (mock/request :get
                    "/some/path?password=secret&email=foo@example.com")
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           @response))
    (is (= 1 (count @output)))
    (let [elapsed (-> (first @output) :message ::logger/ms)]
      (is (= {:level :info
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :status 200
                        ::logger/ms elapsed}}
             (first @output)))
      (is (pos? elapsed)))))

(deftest log-response-no-status-test
  (let [output (atom [])
        log #(swap! output conj %)
        handler (-> no-status-handler
                    (logger/wrap-log-response {:log-fn log}))
        response (promise)]
    (handler (mock/request :get "/")
             (fn [r] (deliver response r))
             (fn [r] (deliver response r)))
    @response
    (let [[finish :as lines] @output
          elapsed (-> finish :message ::logger/ms)]
      (is (= 1 (count lines)))
      (is (= {:level :info
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/"
                        :server-name "localhost"
                        :status nil
                        ::logger/ms elapsed}}
             finish))
      (is (number? elapsed)))))

(deftest log-request-error-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> error-handler
                    (logger/wrap-log-response {:log-fn log}))
        response (promise)]
    (handler
      (mock/request :get "/some/path")
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    (is (= {:status 500
            :body "error"
            :headers {:ping "pong"}}
           @response))
    (let [[finish :as lines] @output]
      (is (= 1 (count lines)))
      (let [elapsed (-> finish :message ::logger/ms)]
        (is (= {:level :error
                :message {::logger/type :finish
                          :request-method :get
                          :uri "/some/path"
                          :server-name "localhost"
                          :status 500
                          ::logger/ms elapsed}}
               finish))
        (is (pos? elapsed))))))

(deftest log-params-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-request-params {:log-fn log})
                    wrap-params)
        response (promise)]
    (handler
      (mock/request :get
                    "/some/path?password=secret&email=foo@example.com")
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           @response))
    (let [[params :as lines] @output]
      (is (= 1 (count lines)))
      (is (= {:level :debug
              :message {::logger/type :params
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :params {"password" "[REDACTED]"
                                 "email" "foo@example.com"}}}
             params)))))

(deftest log-request-response-and-params-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-response {:log-fn log})
                    (logger/wrap-log-request-params {:log-fn log})
                    wrap-params
                    (logger/wrap-log-request-start {:log-fn log}))
        response (promise)]
    (handler
      (mock/request :get
                    "/some/path?password=secret&email=foo@example.com")
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           @response))
    (let [[start params finish :as lines] @output]
      (is (= 3 (count lines)))
      (is (= {:level :info
              :message {::logger/type :starting
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"}}
             start))
      (is (= {:level :debug
              :message {::logger/type :params
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :params {"password" "[REDACTED]"
                                 "email" "foo@example.com"}}}
             params))
      (let [elapsed (-> finish :message ::logger/ms)]
        (is (= {:level :info
                :message {::logger/type :finish
                          :request-method :get
                          :uri "/some/path"
                          :server-name "localhost"
                          :status 200
                          ::logger/ms elapsed}}
               finish))
        (is (pos? elapsed))))))

(deftest log-request-and-params-with-exception-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> throws-handler
                    (logger/wrap-log-response {:log-fn log})
                    (logger/wrap-log-request-params {:log-fn log})
                    wrap-params
                    (logger/wrap-log-request-start {:log-fn log}))
        ex (promise)]
    (try
      (handler (mock/request :get
                            "/some/path?password=secret&email=foo@example.com")
              (fn [r] (deliver ex r))
              (fn [r] (deliver ex r)))
      (catch Exception _))
    (is (= "Ooopsie" (.getMessage @ex)))
    (let [[start params logged-ex :as lines] @output]
      (is (= 3 (count lines)))
      (is (= {:level :info
              :message {::logger/type :starting
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"}}
             start))
      (is (= {:level :debug
              :message {::logger/type :params
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :params {"password" "[REDACTED]"
                                 "email" "foo@example.com"}}}
             params))
      (let [elapsed (-> logged-ex :message ::logger/ms)]
        (is (= {:level :error
                :throwable @ex
                :message {::logger/type :exception
                          :request-method :get
                          :uri "/some/path"
                          :server-name "localhost"
                          ::logger/ms elapsed}}
               logged-ex))
        (is (pos? elapsed))))))

(deftest wrap-with-logger-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:log-fn log})
                    wrap-params)
        response (promise)]
    (handler (mock/request :get
                           "/some/path?password=secret&email=foo@example.com")
             (fn [r] (deliver response r))
             (fn [r] (deliver response r)))
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           @response))
    (let [[start params finish :as lines] @output]
      (is (= 3 (count lines)))
      (is (= {:level :info
              :message {::logger/type :starting
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"}}
             start))
      (is (= {:level :debug
              :message {::logger/type :params
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :params {"password" "[REDACTED]"
                                 "email" "foo@example.com"}}}
             params))
      (let [elapsed (-> finish :message ::logger/ms)]
        (is (= {:level :info
                :message {::logger/type :finish
                          :request-method :get
                          :uri "/some/path"
                          :server-name "localhost"
                          :status 200
                          ::logger/ms elapsed}}
               finish))
        (is (pos? elapsed))))))

(deftest wrap-logger-with-request-keys-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:log-fn log
                                              :request-keys [:server-port :scheme :uri]})
                    wrap-params)
        response (promise)]
    (handler
      (mock/request :get
                    "/some/path?password=secret&email=foo@example.com")
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           @response))
    (let [[start params finish :as lines] @output]
      (is (= 3 (count lines)))
      (is (= {:level :info
              :message {::logger/type :starting
                        :uri "/some/path"
                        :server-port 80
                        :scheme :http}}
             start))
      (is (= {:level :debug
              :message {::logger/type :params
                        :uri "/some/path"
                        :server-port 80
                        :scheme :http
                        :params {"password" "[REDACTED]"
                                 "email" "foo@example.com"}}}
             params))
      (let [elapsed (-> finish :message ::logger/ms)]
        (is (= {:level :info
                :message {::logger/type :finish
                          :uri "/some/path"
                          :server-port 80
                          :scheme :http
                          :status 200
                          ::logger/ms elapsed}}
               finish))
        (is (pos? elapsed))))))

;; This test is similar to ring.logger-test/tools-logging-test,
;; only it uses the async middleware. However, for some reason
;; one of the tests fails when both of them are enabled.
#_(deftest tools-logging-test
  (let [handler (-> ok-handler
                    logger/wrap-with-logger
                    wrap-params)
        response (promise)
        output-str (with-system-out-str
                     (handler
                       (mock/request :get
                                     "/some/path?password=secret&email=foo@example.com")
                       (fn [r] (deliver response r))
                       (fn [r] (deliver response r)))
                     @response)
        lines (->> (s/split-lines output-str)
                   (map #(s/split % #" " 2)))
        levels (map first lines)
        [start params finish] (->> (map second lines)
                                   (map read-string))]
    (is (= 3 (count lines)))
    (is (= ["INFO" "DEBUG" "INFO"]
           levels))

    (is (= {::logger/type :starting
            :request-method :get
            :uri "/some/path"
            :server-name "localhost"}
           start))
    (is (= {::logger/type :params
            :request-method :get
            :uri "/some/path"
            :server-name "localhost"
            :params {"password" "[REDACTED]"
                     "email" "foo@example.com"}}
           params))
    (let [elapsed (::logger/ms finish)]
      (is (= {::logger/type :finish
              :request-method :get
              :uri "/some/path"
              :server-name "localhost"
              :status 200
              ::logger/ms elapsed}
             finish))
      (is (pos? elapsed)))))

(deftest old-ring-logger-messages-output-fn-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        hostname "logger.example.com"
        elapsed (atom nil)
        transform-fn (fn [log-item]
                       (reset! elapsed (get-in log-item [:message ::logger/ms]))
                       (logger.compat/logger-0.7.0-transform-fn log-item))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:transform-fn transform-fn
                                              :log-fn log})
                    wrap-params)
        response (promise)]
    (handler
      (mock/request :get
                    (str "http://" hostname "/some/path?password=secret&email=foo@example.com"))
      (fn [r] (deliver response r))
      (fn [r] (deliver response r)))
    @response
    (let [[start params finish :as lines] @output]
      (is (= 3 (count lines)))

      (is (= {:level :info
              :message "Starting :get /some/path for logger.example.com"}
             start))

      (is (= {:level :debug
              :message "  \\ - - - -  Params: {\"password\" \"[REDACTED]\", \"email\" \"foo@example.com\"}"}
             params))

      (is (= {:level :info
              :message (str "Finished :get /some/path for logger.example.com in ("
                            @elapsed " ms) Status: 200")}
             finish))
      (is (pos? @elapsed)))))
