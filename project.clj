(defproject ring.middleware.logger "0.6.0-SNAPSHOT"
  :description "Ring middleware to log each request."
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojars.pjlegato/clansi "1.3.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [onelog "0.4.5" :scope "provided"]
                 [com.taoensso/timbre "4.1.1" :scope "provided"]]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.2.0"]]}})
