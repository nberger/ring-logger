(defproject ring-logger "0.7.6"
  :description "Log ring requests & responses using your favorite logging backend."
  :url "https://github.com/nberger/ring-logger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojars.pjlegato/clansi "1.3.0"]
                 [org.clojure/tools.logging "0.3.1"]]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.2.0"]
                                  [ring/ring-codec "1.0.0"]]}})
