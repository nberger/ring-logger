(ns ring.logger.messages-test
  (:require [clojure.string :as string]
            [ring.logger.messages :as msg]
            [ring.logger :as logger]
            [ring.logger.protocols :refer [Logger]]
            [clojure.edn :as edn]
            [clojure.test :refer [is deftest testing]]))

(deftest redact-fn-from-options-test
  (testing "default redact-fn"
    (let [options (logger/make-options {})
          m {"authorization" "some-secret-token"
             :password      "123456"
             :user          "john"}]
      (is (= {"authorization" "[REDACTED]"
              :password      "[REDACTED]"
              :user          "john"}
             ((msg/redact-some #{:authorization :password} (constantly "[REDACTED]")) m)
             ((:redact-fn options) m)))))

  (testing "identity as redact-fn"
    (let [options (logger/make-options {:redact-fn identity})
          m {:authorization "some-secret-token"
             :password      "123456"
             :user          "john"}]
      (is (= m
             ((:redact-fn options) m))))))

(deftest default-redacted-headers-test
  (let [options (logger/make-options {})
        m {"authorization" "some-secret-token"
           :password      "123456"
           :user          "john"}]
    (is (= {"authorization" "[REDACTED]"
            :password      "[REDACTED]"
            :user          "john"}
           ((msg/redact-some #{:authorization :password} (constantly "[REDACTED]")) m)
           ((:redact-fn options) m)))))

(deftest redacted-headers-and-params-with-custom-fn-test
  (let [options {:printer :no-color
                 :logger (reify Logger (log [_ _ _ message] message))
                 :redact-fn (msg/redact-some #{:authorization :cookie :password} (constantly "[I WAS REDACTED]"))}
        request {:request-method "GET"
                 :uri "http://example.com/some/endpoint"
                 :remote-addr "172.243.12.12"
                 :query-string "some=query&string"
                 :headers {:authorization "Basic my-fancy-encoded-secret"
                           :cookie "password=password-in-cookie; user=me"}
                 :cookies {:password {:value "password-in-cookie"}
                           :user {:value "me"}}
                 :params {:password "1234"
                          :user "nick"
                          :foo :bar}}
        starting-msg (msg/starting options request)
        logged-params (msg/request-params options request)]
    (let [headers-in-msg (->> starting-msg
                             (drop-while #(not= % \{))
                             (apply str)
                             edn/read-string)]
      (is (= headers-in-msg
             {:authorization "[I WAS REDACTED]"
              :cookie "[I WAS REDACTED]"})))
    (is (= {:password "[I WAS REDACTED]"
            :user "nick"
            :foo :bar}
           (edn/read-string (subs logged-params (count msg/request-params-default-prefix)))))
    (is (= {:password "[I WAS REDACTED]"
            :user "nick"
            :foo :bar}
           (edn/read-string (subs logged-params (count msg/request-params-default-prefix)))))))
