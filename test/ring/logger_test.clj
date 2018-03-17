(ns ring.logger-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [clojure.string :as s]
   [ring.middleware.params :refer [wrap-params]]
   [ring.logger :as logger]
   [ring.mock.request :as mock]))

(def ok-handler
  (fn [req]
    (Thread/sleep 2)
    {:status 200
     :body "ok"
     :headers {:ping "pong"}}))

(deftest log-request-test
  (let [output (atom [])
        log (fn [& args]
              (swap! output conj args))
        handler (-> ok-handler
                    wrap-params
                    (logger/wrap-log-request {:log-fn log
                                               :request-id-fn (constantly 42)}))
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [start finish :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 2 (count lines)))
    (is (= [:info nil {::logger/type :starting
                       :request-method :get
                       :uri "/some/path"
                       ::logger/request-id 42}]
           start))
    (let [elapsed (-> finish last ::logger/ms)]
      (is (= [:info nil {::logger/type :finish
                         :request-method :get
                         :uri "/some/path"
                         :status 200
                         ::logger/request-id 42
                         ::logger/ms elapsed}]
             finish))
      (is (pos? elapsed)))))

(deftest log-params-test
  (let [output (atom [])
        log (fn [& args]
              (swap! output conj args))
        handler (-> ok-handler
                    (logger/wrap-log-params {:log-fn log})
                    wrap-params)
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [params :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 1 (count lines)))
    (is (= [:debug nil {::logger/type :params
                        :request-method :get
                        :uri "/some/path"
                        :params {"password" "secret"
                                 "email" "foo@example.com"}}]
           params))))

(deftest log-request-log-params-test
  (let [output (atom [])
        log (fn [& args]
              (swap! output conj args))
        handler (-> ok-handler
                    (logger/wrap-log-params {:log-fn log})
                    wrap-params
                    (logger/wrap-log-request {:log-fn log
                                               :request-id-fn (constantly 42)}))
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [start params finish :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 3 (count lines)))
    (is (= [:info nil {::logger/type :starting
                       :request-method :get
                       :uri "/some/path"
                       ::logger/request-id 42}]
           start))
    (is (= [:debug nil {::logger/type :params
                        ::logger/request-id 42
                        :request-method :get
                        :uri "/some/path"
                        :params {"password" "secret"
                                 "email" "foo@example.com"}}]
           params))
    (let [elapsed (-> finish last ::logger/ms)]
      (is (= [:info nil {::logger/type :finish
                         :request-method :get
                         :uri "/some/path"
                         :status 200
                         ::logger/request-id 42
                         ::logger/ms elapsed}]
             finish))
      (is (pos? elapsed)))))

(deftest wrap-logger-test
  (let [output (atom [])
        log (fn [& args]
              (swap! output conj args))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:log-fn log
                                              :request-id-fn (constantly 42)})
                    wrap-params)
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [start params finish :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 3 (count lines)))
    (is (= [:info nil {::logger/type :starting
                       :request-method :get
                       :uri "/some/path"
                       ::logger/request-id 42}]
           start))
    (is (= [:debug nil {::logger/type :params
                        ::logger/request-id 42
                        :request-method :get
                        :uri "/some/path"
                        :params {"password" "secret"
                                 "email" "foo@example.com"}}]
           params))
    (let [elapsed (-> finish last ::logger/ms)]
      (is (= [:info nil {::logger/type :finish
                         :request-method :get
                         :uri "/some/path"
                         :status 200
                         ::logger/request-id 42
                         ::logger/ms elapsed}]
             finish))
      (is (pos? elapsed)))))
