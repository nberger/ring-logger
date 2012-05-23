(defproject ring.middleware.logger "0.1.0-SNAPSHOT"
  :description "Ring middleware to log each request using Log4J."
  :plugins [[lein-swank "1.4.4"]]
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.slf4j/slf4j-log4j12 "1.6.4"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-logging-config "1.9.6"]
                 [org.clojars.pjlegato/clansi "1.3.0"]
                 ])