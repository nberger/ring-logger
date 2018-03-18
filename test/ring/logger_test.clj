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

(def throws-handler
  (fn [req]
    (Thread/sleep 2)
    (throw (Exception. "Ooopsie"))))

(deftest log-request-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
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
    (is (= {:level :info
            :message {::logger/type :starting
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      ::logger/request-id 42}}
           start))
    (let [elapsed (-> finish :message ::logger/ms)]
      (is (= {:level :info
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :status 200
                        ::logger/request-id 42
                        ::logger/ms elapsed}}
             finish))
      (is (pos? elapsed)))))

(deftest log-params-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
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
    (is (= {:level :debug
            :message {::logger/type :params
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      :params {"password" "secret"
                               "email" "foo@example.com"}}}
           params))))

(deftest log-request-log-params-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
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
    (is (= {:level :info
            :message {::logger/type :starting
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      ::logger/request-id 42}}
           start))
    (is (= {:level :debug
            :message {::logger/type :params
                      ::logger/request-id 42
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      :params {"password" "secret"
                               "email" "foo@example.com"}}}
           params))
    (let [elapsed (-> finish :message ::logger/ms)]
      (is (= {:level :info
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :status 200
                        ::logger/request-id 42
                        ::logger/ms elapsed}}
             finish))
      (is (pos? elapsed)))))

(deftest log-request-log-params-with-exception-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> throws-handler
                    (logger/wrap-log-params {:log-fn log})
                    wrap-params
                    (logger/wrap-log-request {:log-fn log
                                               :request-id-fn (constantly 42)}))
        ex (try
            (-> (mock/request :get
                              "/some/path?password=secret&email=foo@example.com")
                (handler))
            (catch Exception e e))
        [start params logged-ex :as lines] @output]
    (is (= "Ooopsie" (.getMessage ex)))
    (is (= 3 (count lines)))
    (is (= {:level :info
            :message {::logger/type :starting
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      ::logger/request-id 42}}
           start))
    (is (= {:level :debug
            :message {::logger/type :params
                      ::logger/request-id 42
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      :params {"password" "secret"
                               "email" "foo@example.com"}}}
           params))
    (let [elapsed (-> logged-ex :message ::logger/ms)]
      (is (= {:level :error
              :throwable ex
              :message {::logger/type :exception
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        ::logger/request-id 42
                        ::logger/ms elapsed}}
             logged-ex))
      (is (pos? elapsed)))))

(deftest wrap-logger-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
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
    (is (= {:level :info
            :message {::logger/type :starting
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      ::logger/request-id 42}}
           start))
    (is (= {:level :debug
            :message {::logger/type :params
                      ::logger/request-id 42
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"
                      :params {"password" "secret"
                               "email" "foo@example.com"}}}
           params))
    (let [elapsed (-> finish :message ::logger/ms)]
      (is (= {:level :info
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :status 200
                        ::logger/request-id 42
                        ::logger/ms elapsed}}
             finish))
      (is (pos? elapsed)))))

(deftest tools-logging-test
  (let [handler (-> ok-handler
                    (logger/wrap-with-logger {:request-id-fn (constantly 42)})
                    wrap-params)
        output-str (with-out-str
                     (-> (mock/request :get
                                       "/some/path?password=secret&email=foo@example.com")
                         (handler)))
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
            :server-name "localhost"
            ::logger/request-id 42}
           start))
    (is (= {::logger/type :params
            ::logger/request-id 42
            :request-method :get
            :uri "/some/path"
            :server-name "localhost"
            :params {"password" "secret"
                     "email" "foo@example.com"}}
           params))
    (let [elapsed (::logger/ms finish)]
      (is (= {::logger/type :finish
              :request-method :get
              :uri "/some/path"
              :server-name "localhost"
              :status 200
              ::logger/request-id 42
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
                       (update log-item
                               :message
                               (fn [{:keys [::logger/type request-method uri server-name
                                            ::logger/ms status params]}]
                                 (case type
                                   :starting
                                   (str "Starting " request-method " " uri " for " server-name)

                                   :params
                                   (str "  \\ - - - -  Params: " (pr-str params))

                                   :finish
                                   (do
                                     (reset! elapsed ms)
                                     (str "Finished " request-method " " uri " for " server-name
                                          " in (" ms " ms)"
                                          " Status: " status))))))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:request-id-fn (constantly 42)
                                              :transform-fn transform-fn
                                              :log-fn log})
                    wrap-params)
        response (-> (mock/request :get
                                   (str "http://" hostname "/some/path?password=secret&email=foo@example.com"))
                     (handler))
        [start params finish :as lines] @output]
    (is (= 3 (count lines)))

    (is (= {:level :info
            :message "Starting :get /some/path for logger.example.com"}
           start))

    (is (= {:level :debug
            :message "  \\ - - - -  Params: {\"password\" \"secret\", \"email\" \"foo@example.com\"}"}
           params))

    (is (= {:level :info
            :message (str "Finished :get /some/path for logger.example.com in ("
                          @elapsed " ms) Status: 200")}
           finish))
    (is (pos? @elapsed))))
