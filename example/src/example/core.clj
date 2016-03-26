(ns example.core
  (:require [clj-http.client :as http]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.adapter.jetty :as jetty]
            [ring.logger :as logger]))

(defroutes handler
  (GET "/" [name] (format "<h1>Hello, %s!</h1>" name))
  (POST "/throws" [] (throw (Exception. "Oops, sooooorry")))
  (route/not-found "<h1>Page not found</h1>"))

(defn run [app]
  (let [server (jetty/run-jetty app
                                {:port 14587
                                 :join? false})]

    ; Hello ring-logger!
    (http/get "http://localhost:14587/?name=ring-logger"
              {:headers {"foo" "baz"
                         "AuThorization" "Basic super-secret!"}})

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

(defn -main [& args]
  (run (-> handler
           logger/wrap-with-logger
           wrap-keyword-params
           wrap-nested-params
           wrap-params
           logger/wrap-with-body-logger)))
