(ns ring.logger-test
  (:refer-clojure :exclude [error-handler])
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [clojure.string :as s]
   [ring.middleware.params :refer [wrap-params]]
   [ring.logger :as logger]
   [ring.logger.compat :as logger.compat]
   [ring.mock.request :as mock])
  (:import
   [java.io ByteArrayOutputStream PrintStream]
   [org.apache.log4j LogManager]))

(def ok-handler
  (fn [req]
    (Thread/sleep 2)
    {:status 200
     :body "ok"
     :headers {:ping "pong"}}))

(def error-handler
  (fn [req]
    (Thread/sleep 2)
    {:status 500
     :body "error"
     :headers {:ping "pong"}}))

(def throws-handler
  (fn [req]
    (Thread/sleep 2)
    (throw (Exception. "Ooopsie"))))

(deftest log-request-start-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-request-start {:log-fn log}))
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [start :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 1 (count lines)))
    (is (= {:level :info
            :message {::logger/type :starting
                      :request-method :get
                      :uri "/some/path"
                      :server-name "localhost"}}
           start))))

(deftest log-response-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-response {:log-fn log}))
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [finish :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 1 (count lines)))
    (let [elapsed (-> finish :message ::logger/ms)]
      (is (= {:level :info
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :status 200
                        ::logger/ms elapsed}}
             finish))
      (is (pos? elapsed)))))

(deftest custom-response-status-config-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-response {:log-fn log
                                               :status-to-log-level-fn (fn [status] (if (= 200 status) :fatal :trace))}))
        response (-> (mock/request :get
                                   "/some/path?password=secret&email=foo@example.com")
                     (handler))
        [finish :as lines] @output]
    (is (= {:status 200
            :body "ok"
            :headers {:ping "pong"}}
           response))
    (is (= 1 (count lines)))
    (let [elapsed (-> finish :message ::logger/ms)]
      (is (= {:level :fatal
              :message {::logger/type :finish
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        :status 200
                        ::logger/ms elapsed}}
             finish))
      (is (pos? elapsed)))))

(deftest log-response-no-status-test
  (let [output (atom [])
        log #(swap! output conj %)
        handler (-> (constantly {:body "ok"})
                    (logger/wrap-log-response {:log-fn log}))]
    (handler (mock/request :get "/"))
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
        response (-> (mock/request :get "/some/path")
                     (handler))
        [finish :as lines] @output]
    (is (= {:status 500
            :body "error"
            :headers {:ping "pong"}}
           response))
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
      (is (pos? elapsed)))))

(deftest log-params-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-request-params {:log-fn log})
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
                      :params {"password" "[REDACTED]"
                               "email" "foo@example.com"}}}
           params))))

(deftest log-request-response-and-params-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-log-response {:log-fn log})
                    (logger/wrap-log-request-params {:log-fn log})
                    wrap-params
                    (logger/wrap-log-request-start {:log-fn log}))
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
      (is (pos? elapsed)))))

(deftest log-request-and-params-with-exception-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> throws-handler
                    (logger/wrap-log-response {:log-fn log})
                    (logger/wrap-log-request-params {:log-fn log})
                    wrap-params
                    (logger/wrap-log-request-start {:log-fn log}))
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
              :throwable ex
              :message {::logger/type :exception
                        :request-method :get
                        :uri "/some/path"
                        :server-name "localhost"
                        ::logger/ms elapsed}}
             logged-ex))
      (is (pos? elapsed)))))

(deftest wrap-with-logger-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:log-fn log})
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
      (is (pos? elapsed)))))

(deftest wrap-logger-with-request-keys-test
  (let [output (atom [])
        log (fn [message]
              (swap! output conj message))
        handler (-> ok-handler
                    (logger/wrap-with-logger {:log-fn log
                                              :request-keys [:server-port :scheme :uri]})
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
      (is (pos? elapsed)))))

(defmacro with-system-out-str [& body]
  `(let [out-buffer# (ByteArrayOutputStream.)
         original-out# System/out
         tmp-out# (PrintStream. out-buffer# true "UTF-8")]
     (try
       (System/setOut tmp-out#)
       ;; The console appender needs to be re-activated to ensure it sees the new value of System/out
       (.activateOptions (.getAppender (LogManager/getRootLogger) "console"))
       ~@body
       (finally
         (System/setOut original-out#)))
     (.toString out-buffer# "UTF-8")))

(deftest tools-logging-test
  (let [handler (-> ok-handler
                    logger/wrap-with-logger
                    wrap-params)
        output-str (with-system-out-str
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
        response (-> (mock/request :get
                                   (str "http://" hostname "/some/path?password=secret&email=foo@example.com"))
                     (handler))
        [start params finish :as lines] @output]
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
    (is (pos? @elapsed))))
