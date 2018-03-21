(ns example.core
  (:require [clj-http.client :as http]
            clj-http.cookies
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.adapter.jetty :as jetty]
            [ring.logger :as logger]))

(defroutes handler
  (GET "/" [name] {:body (format "<h1>Hello, %s!</h1>" name)
                   :status 200
                   :cookies {"password" {:value "007"}
                             "user" {:value "you"}} })
  (POST "/throws" [] (throw (Exception. "Oops, sooooorry")))
  (route/not-found "<h1>Page not found</h1>"))

(defn run [app]
  (let [server (jetty/run-jetty app {:port 14587
                                     :join? false})
        cookie-store (clj-http.cookies/cookie-store)]

    ; Hello ring-logger!
    (println "\n\nA request with a password param which gets redacted\n")
    (http/get "http://localhost:14587/?name=ring-logger&password=secret"
              {:headers {"foo" "baz"
                         "AuThorization" "Basic super-secret!"}
               :cookie-store cookie-store})

    ; not found
    (println "\n\nNot found route\n")
    (try
      (http/get "http://localhost:14587/not-found")
      ; ignore
      (catch Throwable t))

    ; throws
    (println "\n\nA route that throws an exception\n")
    (try
      (http/post "http://localhost:14587/throws" {:form-params {:foo "bar"
                                                                :nested {:password "5678"
                                                                         :id 1}
                                                                :password "1234"}})
      ; ignore
      (catch Throwable t))

    (println "\n\nWe are done here.")

    ; stop server
    (.stop server)))

(def ^:dynamic *request-id* nil)

(defn add-request-id [handler]
  (fn [request]
    (handler (assoc request :request-id (rand-int 0xffff)))))

(defn -main [& args]
  (run (-> handler
           logger/wrap-with-logger
           wrap-cookies
           wrap-keyword-params
           wrap-nested-params
           wrap-params))

  ;; this second example prefixes every log line with a unique request-id
  (println "\n\n*** Same example but adding a :request-id to every log message")
  (run (-> handler
           (logger/wrap-with-logger {:request-keys (conj logger/default-request-keys :request-id)})
           add-request-id
           wrap-cookies
           wrap-keyword-params
           wrap-nested-params
           wrap-params)))
