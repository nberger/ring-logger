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
            [ring.logger :as logger]
            [ring.logger.protocols :as logger.protocols]
            [ring.logger.tools-logging :refer [make-tools-logging-logger]]))

(defroutes handler
  (GET "/" [name] {:body (format "<h1>Hello, %s!</h1>" name)
                   :status 200
                   :cookies {"password" {:value "007"}
                             "user" {:value "you"}} })
  (POST "/throws" [] (throw (Exception. "Oops, sooooorry")))
  (route/not-found "<h1>Page not found</h1>"))

(defn run [app]
  (let [server (jetty/run-jetty app
                                {:port 14587
                                 :join? false})
        cookie-store (clj-http.cookies/cookie-store)]

    ; Hello ring-logger!
    (http/get "http://localhost:14587/?name=ring-logger"
              {:headers {"foo" "baz"
                         "AuThorization" "Basic super-secret!"}
               :cookie-store cookie-store})

    ; Hello Nico!
    (http/get "http://localhost:14587/?name=Nico&password=pass"
              {:headers {"foo" "baz"
                         "AuThorization" "Basic super-secret!"}
               :cookie-store cookie-store})

    ; not found
    (try
      (http/get "http://localhost:14587/not-found")
      ; ignore
      (catch Throwable t))

    ; throws
    (try
      (http/post "http://localhost:14587/throws" {:form-params {:foo "bar"
                                                                :nested {:password "5678"
                                                                         :id 1}
                                                                :password "1234"}})
      ; ignore
      (catch Throwable t))

    (println "Done. See that awesome log. I'm stopping the server now...")

    ; stop server
    (.stop server)))

(def ^:dynamic *request-id* nil)

(defn add-request-id [handler]
  (fn [request]
    (binding [*request-id* (rand-int 0xffff)]
      (handler request))))

(defn make-with-request-id-logger []
  (let [logger (make-tools-logging-logger)]
    (reify logger.protocols/Logger
      (add-extra-middleware [_ handler] handler)
      (log [_ level throwable message]
        (logger.protocols/log logger level throwable (format "(%04x) %s" *request-id* message))))))

(defn -main [& args]
  (run (-> handler
           logger/wrap-with-logger
           wrap-cookies
           wrap-keyword-params
           wrap-nested-params
           wrap-params
           logger/wrap-with-body-logger))

  ;; this second example prefixes every log line with a unique request-id
  (run (-> handler
           (logger/wrap-with-logger {:logger (make-with-request-id-logger)})
           add-request-id
           wrap-cookies
           wrap-keyword-params
           wrap-nested-params
           wrap-params
           logger/wrap-with-body-logger)))
