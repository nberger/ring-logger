(ns ring.logger.messages
  (:require [clansi.core :as ansi]))

(defn get-printer
  [{:keys [printer]} & more]
  printer)

(defmulti starting get-printer)

(defmethod starting :default
  [{:keys [info] :as options}
   {:keys [request-method uri remote-addr query-string headers params] :as req}]
  (let [headers (dissoc headers "authorization")]
    (info (str (ansi/style "Starting " :cyan)
             request-method " "
             uri (if query-string (str "?" query-string))
             " for " remote-addr
             " " headers))))

(defmulti request-details get-printer)

(defmethod request-details :default
  [{:keys [debug] :as options} req]
  (debug (str "Request details: " (select-keys req [:character-encoding
                                                    :content-length
                                                    :content-type
                                                    :query-string
                                                    :remote-addr
                                                    :request-method
                                                    :scheme
                                                    :server-name
                                                    :server-port
                                                    :uri]))))

(defmulti request-params get-printer)

(defmethod request-params :default
  [{:keys [info]} {:keys [params]}]
  (when params
    (info (str "  \\ - - - -  Params: " params))))

(defmulti sending-response get-printer)

(defmethod sending-response :default
  [{:keys [trace]} response]
  (trace (str "[ring] Sending response: " response)))

(defmulti finished get-printer)

(defmethod finished :default
  [{:keys [error info trace] :as options}
   {:keys [request-method uri remote-addr query-string] :as req}
   {:keys [status] :as resp}
   totaltime]
  (let [colortime (try (apply ansi/style
                              (str totaltime)
                              (cond
                                (>= totaltime 1500)  [:bright :red]
                                (>= totaltime 800)   [:red]
                                (>= totaltime 500)   [:yellow]
                                :else :default))
                       (catch Exception e (or totaltime "??")))

        colorstatus (try (apply ansi/style
                                (str status)
                                (cond
                                  (< status 300)  [:default]
                                  (>= status 500) [:bright :red]
                                  (>= status 400) [:red]
                                  :else           [:yellow]))
                         (catch Exception e (or status "???")))
        log-message (str (ansi/style "Finished " :cyan)
                         request-method " "
                         uri  (if query-string (str "?" query-string))
                         " for " remote-addr
                         " in (" colortime " ms)"
                         " Status: " colorstatus

                         (when (= status 302)
                           (str " redirect to " (get-in resp [:headers "Location"]))))]

    (if (and (number? status) (>= status 500))
      (error log-message)
      (info  log-message))))

(defmulti exception get-printer)

(defmethod exception :default
  [{:keys [error error-with-ex] :as options}
   {:keys [request-method uri remote-addr] :as request}
   throwable totaltime]
  (error (str (ansi/style "Uncaught exception processing request:" :bright :red)  " for " remote-addr " in (" totaltime " ms) - request was: " request))
  (error-with-ex throwable ""))
