(ns ring.logger.messages
  (:require [clansi.core :as ansi]
            [ring.logger.protocols :refer [debug error info trace]]))

(defn get-printer
  [{:keys [printer]} & more]
  printer)

(defmulti starting get-printer)

(defn- make-default-starting-message
  [options {:keys [request-method uri remote-addr query-string headers params] :as req}]
  (let [headers (dissoc headers "authorization")]
    (str request-method " "
         uri (if query-string (str "?" query-string))
         " for " remote-addr
         " " headers)))

(defmethod starting :default
  [{:keys [logger] :as options} req]
  (info logger (str (ansi/style "Starting " :cyan)
                    (make-default-starting-message options req))))

(defmethod starting :no-color
  [{:keys [logger] :as options} req]
  (info logger (str "Starting "
                    (make-default-starting-message options req))))

(defmulti request-details get-printer)

(defmethod request-details :default
  [{:keys [logger] :as options} req]
  (debug logger (str "Request details: " (select-keys req [:character-encoding
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
  [{:keys [logger]} {:keys [params]}]
  (when params
    (info logger (str "  \\ - - - -  Params: " params))))

(defmulti sending-response get-printer)

(defmethod sending-response :default
  [{:keys [logger]} response]
  (trace logger (str "[ring] Sending response: " response)))

(defmulti finished get-printer)

(defn- make-and-log-finished-message
  [{:keys [logger timing] :as options}
   {:keys [request-method uri remote-addr query-string
           logger-start-time logger-end-time] :as req}
   {:keys [status] :as resp}
   title time-str status-str]
  (let [log-message (str title
                         request-method " "
                         uri  (if query-string (str "?" query-string))
                         " for " remote-addr
                         (when timing (str " in (" time-str " ms)"))
                         " Status: " status-str

                         (when (= status 302)
                           (str " redirect to " (get-in resp [:headers "Location"]))))]

    (if (and (number? status) (>= status 500))
      (error logger log-message)
      (info  logger log-message))))

(defn- get-total-time [{:keys [logger-start-time logger-end-time] :as req}]
  (- logger-end-time logger-start-time))

(defmethod finished :default
  [{:keys [timing] :as options} req {:keys [status] :as resp}]
  (let [time-str (when timing
                   (let [total-time (get-total-time req)]
                     (try (apply ansi/style
                                 (str total-time)
                                 (cond
                                   (>= total-time 1500)  [:bright :red]
                                   (>= total-time 800)   [:red]
                                   (>= total-time 500)   [:yellow]
                                   :else :default))
                          (catch Exception e (or total-time "??")))))

        status-str (try (apply ansi/style
                                (str status)
                                (cond
                                  (< status 300)  [:default]
                                  (>= status 500) [:bright :red]
                                  (>= status 400) [:red]
                                  :else           [:yellow]))
                         (catch Exception e (or status "???")))
        title (ansi/style "Finished " :cyan)]
    (make-and-log-finished-message options req resp title time-str status-str)))

(defmethod finished :no-color
  [{:keys [timing] :as options} req {:keys [status] :as resp}]
  (make-and-log-finished-message options
                                 req
                                 resp
                                 "Finished "
                                 (when timing (get-total-time req))
                                 (str status)))

(defmulti exception get-printer)

(defmethod exception :default
  [{:keys [logger timing] :as options}
   {:keys [request-method uri remote-addr] :as request}
   throwable]
  (error logger (str (ansi/style "Uncaught exception processing request:" :bright :red)
                     " for " remote-addr
                     (when timing " in (" (get-total-time request) " ms)")
                     " - request was: " request))
  (error logger throwable ""))

(defmethod exception :no-color
  [{:keys [logger timing] :as options}
   {:keys [request-method uri remote-addr] :as request}
   throwable]
  (error logger (str "Uncaught exception processing request:"
                     " for " remote-addr
                     (when timing " in (" (get-total-time request) " ms)")
                     " - request was: " request))
  (error logger throwable ""))
