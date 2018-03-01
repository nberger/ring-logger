(ns ring.logger-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [ring.logger :refer [wrap-with-logger wrap-with-body-logger]]
            [ring.logger.protocols :as protocols]
            [ring.util.codec :as codec]
            [ring.mock.request :as mock]))

(def ^{:dynamic true} *entries* (atom []))

(defn make-test-logger []
  (reify protocols/Logger
    (add-extra-middleware [_ handler] handler)
    (log [_ level throwable message]
      (swap! *entries* conj [nil level nil message]))))

(use-fixtures :each
  (fn [f]
    (f)
    (swap! *entries* (constantly []))))

(deftest basic-ok-request-logging
  (let [handler (-> (fn [req]
                      {:status 200
                       :body "ok"
                       :headers {:a "header in the response"}})
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (= [:info :debug :trace :info] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))
      (is (re-find #"Finished [m\^\[0-9]+:get /doc/10 for localhost in \(\d+ ms\) Status:.*200"
                   (-> entries last (nth 3)))))))

(deftest basic-ok-body-logging
  (let [handler (-> (fn [req]
                      {:status 200
                       :body "ok"
                       :headers {:a "header in the response"}})
                    (wrap-with-body-logger (make-test-logger)))
        params {:foo :bar :zoo 123}]
    (handler (-> (mock/request :post "/doc/10")
                 (mock/body params)))
    (let [entries @*entries*]
      (is (= [:debug] (map second entries)))
      (is (= (str "-- Raw request body: '" (codec/form-encode params) "'")
             (-> entries first (nth 3)))))))

(deftest no-color-ok-request-logging
  (let [handler (-> (fn [req]
                      {:status 200
                       :body "ok"
                       :headers {:a "header in the response"}})
                    (wrap-with-logger {:logger (make-test-logger)
                                       :printer :no-color}))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (= [:info :debug :trace :info] (map second entries)))
      (is (re-find #"^Starting :get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))
      (is (re-find #"^Finished :get /doc/10 for localhost in \(\d+ ms\) Status: 200"
                   (-> entries last (nth 3)))))))

(deftest basic-error-request-logging
  (let [handler (-> (fn [req]
                      {:status 500
                       :body "Oh noes!"
                       :headers {:a "header in the response"}})
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (= [:info :debug :trace :error] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))
      (is (re-find #"Finished.*get /doc/10 for localhost in \(\d+ ms\) Status:.*500"
                   (-> entries last (nth 3)))))))

(deftest no-timing-option
  (let [handler (-> (fn [req]
                      {:status 200
                       :body "ok"})
                    (wrap-with-logger {:logger (make-test-logger)
                                       :timing false}))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (re-find #"Finished [m\^\[0-9]+:get /doc/10 for localhost Status:.*200"
                   (-> entries last (nth 3)))))))

(deftest exception-logging-enabled
  (let [handler (-> (fn [req] (throw (Exception. "Oops, I throw sometimes...")))
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (try
      (handler (mock/request :get "/doc/10"))
      (catch Exception e))
    (let [entries @*entries*]
      (is (= [:info :debug :error] (map second entries)))
      (is (re-find #"Oops, I throw sometimes.*processing request.*for localhost in \(\d+ ms\)"
                   (-> entries (nth 2) (nth 3)))))))

(deftest exceptions-option
  (let [handler (-> (fn [req] (throw (Exception. "Oops, I throw sometimes...")))
                    (wrap-with-logger {:logger (make-test-logger)
                                       :exceptions false}))]
    (try
      (handler (mock/request :get "/doc/10"))
      (catch Exception e))
    (let [entries @*entries*]
      (is (= [:info :debug] (map second entries))))))

(deftest redact-authorization-header
  (let [handler (-> (fn [req] {:status 200 :body "Hello!"})
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (handler (-> (mock/request :get "/")
                 (mock/header "AuthoRizaTion" "Basic secret")))
    (println @*entries*)
    (is (= []
           (->> (map #(nth % 3) @*entries*)
                (filter #(re-find #"secret" %)))))))

(deftest basic-ok-request-logging-without-query-params
  (let [handler (-> (fn [req]
                      {:status 200
                       :body "ok" })
                    (wrap-with-logger {:logger (make-test-logger)
                                       :log-query-string? false}))]
    (handler (mock/request :get "/doc/10?limit=10&secret_access_token=****"))
    (let [entries @*entries*]
      (is (not (re-find #"secret_access_token" (-> entries first (nth 3)))))
      (is (not (re-find #"limit" (-> entries first (nth 3)))))
      (is (not (re-find #"query-string" (-> entries second (nth 3)))))
      (is (not (re-find #"secret_access_token" (-> entries last (nth 3)))))
      (is (not (re-find #"limit" (-> entries last (nth 3))))))))

;; async
(deftest async-basic-ok-request-logging
  (let [handler (-> (fn [req respond raise]
                      (respond {:status 200
                                :body "ok"
                                :headers {:a "header in the response"}}))
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (handler (mock/request :get "/doc/10") identity identity)
    (let [entries @*entries*]
      (is (= [:info :debug :trace :info] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))
      (is (re-find #"Finished [m\^\[0-9]+:get /doc/10 for localhost in \(\d+ ms\) Status:.*200"
                   (-> entries last (nth 3)))))))

(deftest async-basic-ok-body-logging
  (let [handler (-> (fn [req respond raise]
                      (respond {:status 200
                                :body "ok"
                                :headers {:a "header in the response"}}))
                    (wrap-with-body-logger (make-test-logger)))
        params {:foo :bar :zoo 123}]
    (handler (-> (mock/request :post "/doc/10")
                 (mock/body params))
             identity
             identity)
    (let [entries @*entries*]
      (is (= [:debug] (map second entries)))
      (is (= (str "-- Raw request body: '" (codec/form-encode params) "'")
             (-> entries first (nth 3)))))))

(deftest async-no-color-ok-request-logging
  (let [handler (-> (fn [req respond raise]
                      (respond {:status 200
                                :body "ok"
                                :headers {:a "header in the response"}}))
                    (wrap-with-logger {:logger (make-test-logger)
                                       :printer :no-color}))]
    (handler (mock/request :get "/doc/10") identity identity)
    (let [entries @*entries*]
      (is (= [:info :debug :trace :info] (map second entries)))
      (is (re-find #"^Starting :get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))
      (is (re-find #"^Finished :get /doc/10 for localhost in \(\d+ ms\) Status: 200"
                   (-> entries last (nth 3)))))))

(deftest async-basic-error-request-logging
  (let [handler (-> (fn [req respond raise]
                      (respond {:status 500
                                :body "Oh noes!"
                                :headers {:a "header in the response"}}))
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (handler (mock/request :get "/doc/10") identity identity)
    (let [entries @*entries*]
      (is (= [:info :debug :trace :error] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))
      (is (re-find #"Finished.*get /doc/10 for localhost in \(\d+ ms\) Status:.*500"
                   (-> entries last (nth 3)))))))

(deftest async-no-timing-option
  (let [handler (-> (fn [req respond raise]
                      (respond {:status 200
                                :body "ok"}))
                    (wrap-with-logger {:logger (make-test-logger)
                                       :timing false}))]
    (handler (mock/request :get "/doc/10") identity identity)
    (let [entries @*entries*]
      (is (re-find #"Finished [m\^\[0-9]+:get /doc/10 for localhost Status:.*200"
                   (-> entries last (nth 3)))))))

(deftest async-exception-logging-enabled
  (let [handler (-> (fn [req respond raise] (throw (Exception. "Oops, I throw sometimes...")))
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (try
      (handler (mock/request :get "/doc/10") identity identity)
      (catch Exception e))
    (let [entries @*entries*]
      (is (= [:info :debug :error] (map second entries)))
      (is (re-find #"Oops, I throw sometimes.*processing request.*for localhost in \(\d+ ms\)"
                   (-> entries (nth 2) (nth 3)))))))

(deftest async-exception-raise-logging-enabled
  (let [handler (-> (fn [req respond raise] (raise (Exception. "Oops, I throw sometimes...")))
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (try
      (handler (mock/request :get "/doc/10") identity identity)
      (catch Exception e))
    (let [entries @*entries*]
      (is (= [:info :debug :error] (map second entries)))
      (is (re-find #"Oops, I throw sometimes.*processing request.*for localhost in \(\d+ ms\)"
                   (-> entries (nth 2) (nth 3)))))))

(deftest async-exceptions-option
  (let [handler (-> (fn [req respond raise] (throw (Exception. "Oops, I throw sometimes...")))
                    (wrap-with-logger {:logger (make-test-logger)
                                       :exceptions false}))]
    (try
      (handler (mock/request :get "/doc/10") identity identity)
      (catch Exception e))
    (let [entries @*entries*]
      (is (= [:info :debug] (map second entries))))))

(deftest async-redact-authorization-header
  (let [handler (-> (fn [req respond raise] (respond {:status 200 :body "Hello!"}))
                    (wrap-with-logger {:logger (make-test-logger)}))]
    (handler (-> (mock/request :get "/")
                 (mock/header "AuthoRizaTion" "Basic secret"))
             identity
             identity)
    (println @*entries*)
    (is (= []
           (->> (map #(nth % 3) @*entries*)
                (filter #(re-find #"secret" %)))))))
