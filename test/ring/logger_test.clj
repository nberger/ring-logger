(ns ring.logger-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [ring.logger :refer [wrap-with-logger]]
            [ring.logger.protocols :as protocols]
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
      (= [:info :debug :error] (map second entries))
      (is (re-find #"Oops, I throw sometimes"
                   (-> entries (nth 2) (nth 3)))))))
