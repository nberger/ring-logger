(defproject ring-logger "0.7.0-SNAPSHOT"
  :description "Ring middleware to log each request."
  :url "https://github.com/nberger/ring-logger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojars.pjlegato/clansi "1.3.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.taoensso/timbre "4.1.1" :scope "provided"]]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.2.0"]]}})
