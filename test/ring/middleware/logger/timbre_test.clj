(ns ring.middleware.logger.timbre-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as timbre]
            [ring.middleware.logger.timbre :refer [make-timbre-logger]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.mock.request :as mock]))

(def ^{:dynamic true} *entries* (atom []))

(defn atom-appender
  [a]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [{:keys [level ?ns-str output-fn] :as data}]
     (swap! a conj [?ns-str level nil (output-fn data)]))})

(defn make-timbre-test-config []
  (merge timbre/example-config
         {:level :trace
          :appenders {:atom (atom-appender *entries*)}}))

(use-fixtures :once
  (fn [f]
    (timbre/with-config (make-timbre-test-config)
      (f))))

(use-fixtures :each
  (fn [f]
    (f)
    (swap! *entries* (constantly []))))

(deftest basic-ok-request-logging
  (let [handler (-> (fn [req]
                      {:status 200
                       :body "ok"
                       :headers {:a "header in the response"}})
                    (wrap-with-logger :logger-impl (make-timbre-logger)))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (= [:info :debug :trace :info] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))   
      (is (re-find #"Finished.*get /doc/10 for localhost in \(\d+ ms\) Status:.*200"
                   (-> entries last (nth 3)))))))

(deftest basic-error-request-logging
  (let [handler (-> (fn [req]
                      {:status 500
                       :body "Oh noes!"
                       :headers {:a "header in the response"}})
                    (wrap-with-logger :logger-impl (make-timbre-logger)))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (= [:info :debug :trace :error] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))   
      (is (re-find #"Finished.*get /doc/10 for localhost in \(\d+ ms\) Status:.*500"
                   (-> entries last (nth 3)))))))

(deftest basic-error-with-exception-request-logging
  (let [handler (-> (fn [req]
                      (throw (Exception. "I'm a handler that throws!")))
                    (wrap-with-logger :logger-impl (make-timbre-logger)))]
    (is (thrown-with-msg? Exception #"handler that throws"
                          (handler (mock/request :get "/doc/10"))))
    (let [entries @*entries*]
      (is (= [:info :debug :error :error] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #"Uncaught exception processing request.*for localhost in \(\d+ ms\)"
                   (-> entries (nth 2) (nth 3))))
      (is (not (re-find #"Finished" (-> entries last (nth 3)))))
      (is (re-find #"I'm a handler that throws\!"
                   (-> entries (nth 3) (nth 3)))))))
