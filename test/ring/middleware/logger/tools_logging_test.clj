(ns ring.middleware.logger.tools-logging-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :refer :all]
            [clojure.tools.logging.impl :as impl]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.mock.request :as mock]))

(def ^{:dynamic true} *entries* (atom []))

(defn test-factory [enabled-set]
  (reify impl/LoggerFactory
    (name [_] "test factory")
    (get-logger [_ log-ns]
      (reify impl/Logger
        (enabled? [_ level] (contains? enabled-set level))
        (write! [_ lvl ex msg]
          (swap! *entries* conj [(str log-ns) lvl ex msg]))))))

(use-fixtures :once
  (fn [f]
    (binding [*logger-factory*
              (test-factory #{:trace :debug :info :warn :error :fatal})]
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
                    (wrap-with-logger))]
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
                      (println "handler")
                      {:status 500
                       :body "Oh noes!"
                       :headers {:a "header in the response"}})
                    (wrap-with-logger))]
    (handler (mock/request :get "/doc/10"))
    (let [entries @*entries*]
      (is (= [:info :debug :trace :error] (map second entries)))
      (is (re-find #"Starting.*get /doc/10 for localhost"
                   (-> entries first (nth 3))))
      (is (re-find #":headers \{:a \"header in the response\"\}"
                   (-> entries (nth 2) (nth 3))))   
      (is (re-find #"Finished.*get /doc/10 for localhost in \(\d+ ms\) Status:.*500"
                   (-> entries last (nth 3)))))))
