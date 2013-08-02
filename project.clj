(defproject ring.middleware.logger "0.4.3"
  :description "Ring middleware to log each request using Log4J."
  :plugins [[lein-swank "1.4.4"]]
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [onelog "0.4.3"]
                 [org.clojars.pjlegato/clansi "1.3.0"]
                 ])