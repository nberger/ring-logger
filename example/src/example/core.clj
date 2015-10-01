(ns example.core
  (:require [clj-http.client :as http]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [ring.logger :as logger]))

(defroutes handler
  (GET "/" [name] (format "<h1>Hello, %s!</h1>" name))
  (POST "/throws" [] (throw (Exception. "Oops, sooooorry")))
  (route/not-found "<h1>Page not found</h1>"))

(def app (-> handler
             logger/wrap-with-logger
             logger/wrap-with-body-logger))

(defn -main [& args]
  (let [server (jetty/run-jetty app
                                {:port 14587
                                 :join? false})]

    ; Hello ring-logger!
    (http/get "http://localhost:14587/?name=ring-logger"
              {:headers {"foo" "baz"}})

    ; not found
    (try
      (http/get "http://localhost:14587/not-found")
      ; ignore
      (catch Throwable t))

    ; throws
    (try
      (http/post "http://localhost:14587/throws" {:form-params {:foo "bar"}})
      ; ignore
      (catch Throwable t))

    (println "Done. See that awesome log. I'm stopping the server now...")

    ; stop server
    (.stop server)))
