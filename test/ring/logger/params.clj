(ns ring.logger.params
  (:require [clojure.test :refer [is deftest testing]]
            [ring.mock.request :as mock]
            [ring.middleware.params :refer [wrap-params]]
            [ring.logger.params :as logger.params]
            [ring.logger :as logger]))

(def ok-handler
  (fn [req]
    {:status 200
     :body "ok"
     :headers {:ping "pong"}}))

(deftest redact-params-basic-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-request-params {:log-fn log})
                    wrap-params)
        response (-> (mock/request :get
                                   "/some/path?password=secret-pass&email=foo@example.com&token=secret-token")
                     (handler))
        [params] @output]
    (is (= {:level :debug
            :message {::logger/type :params
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      :params {"password" "[REDACTED]"
                               "email" "foo@example.com"
                               "token" "[REDACTED]"}}}
           params))))

(deftest redact-params-custom-keys-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-request-params {:log-fn log
                                             :redact-key? #{:my-secret}})
                    wrap-params)
        response (-> (mock/request :get
                                   "/some/path?my-secret=123456")
                     (handler))
        [params] @output]
    (is (= {:level :debug
            :message {::logger/type :params
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      :params {"my-secret" "[REDACTED]"}}}
           params))))
