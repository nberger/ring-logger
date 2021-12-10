(defproject ring-logger "1.0.2-SNAPSHOT"
  :description "Log ring requests & responses using your favorite logging backend."
  :url "https://github.com/nberger/ring-logger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "1.2.1"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :dev {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [ring/ring-mock "0.2.0"]
                                  [ring/ring-core "1.6.3"]
                                  [ring/ring-codec "1.0.0"]
                                  [log4j "1.2.16"]]
                   :resource-paths ["test-resources"]}}

  :plugins [[lein-codox "0.10.3"]]

  :codox {:source-uri "https://github.com/nberger/ring-logger/blob/master/{filepath}#L{line}"}

  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}

  :aliases {"test-all" ["with-profile" "dev,test,1.6:dev,test,1.7:dev,test,1.8:dev,test,1.9" "test"]
            "check-all" ["with-profile" "1.6:1.7:1.8:1.9" "check"]})
