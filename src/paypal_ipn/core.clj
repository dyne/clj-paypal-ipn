;; Copyright (c) 2014 Small Helm LLC
;; Copyright (c) 2017 Dyne.org Foundation

;; The MIT License (MIT)

;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(ns paypal-ipn.core
  (:require [clojure.string :refer [join split trim]]
            [clj-http.client :as http-client]))

(defn parse-paypal-ipn-string [str]
  (if-not (string? str)
    {}
    (->> (split str #"\&")
         (map (fn [arg]
                (split arg #"\=")))
         (filter (fn [keyval]
                   (= 2 (count keyval))))
         (map (fn [[key val]]
                {key (java.net.URLDecoder/decode val)}))
         (apply merge {}))))

(def ipn-must-have-keys ["txn_id" "mc_currency" "mc_gross" "mc_fee" "receiver_id"])
(defn ipn-data-has-essentials? [ipn-data]
  (= 0 (count (filter (fn [key]
                        (not (contains? ipn-data key))) ipn-must-have-keys))))

(defn ask!-paypal [req-body sandbox?]
  (try
    (http-client/post (str "https://www." (if sandbox? "sandbox." "") "paypal.com/cgi-bin/webscr")
                      {:headers {"Connection"  "Close"
                                 "ContentType" "application/x-www-form-urlencoded"}
                       :body req-body
                       :socket-timeout (* 10 1000)
                       :conn-timeout   (* 10 1000)})
    (catch Throwable t
      t)))

(defn handle-ipn
  ([ipn-data on-success on-failure] (handle-ipn ipn-data on-success on-failure false))
  ([ipn-data on-success on-failure sandbox?]
   (if-not (ipn-data-has-essentials? ipn-data) 
     ;; don't even make an http call if it's bad ipn data
     (on-failure "Missing keys")

     (let [req-body (str "cmd=_notify-validate&"
                         (->> ipn-data
                              (map (fn [[key val]]
                                     (str key "=" (java.net.URLEncoder/encode val))))
                              (join "&")))
           response (ask!-paypal req-body sandbox?)]
       (if (and (map? response)
                (contains? response :body)
                (string? (:body response))
                (= "VERIFIED" (trim (:body response))))
         (on-success ipn-data)
         (on-failure response))))))

;; stuff for ring/compjure
(defn req->raw-body-str [req reset?]
  (let [is (:body req)]
    (do
      ;; TODO: fix bug when sending empty post-data
      ;; i.e. curl -i --data "a" http://localhost:8080/paypal/ipn
      (when reset? (.reset is))
      (let [raw-body-str (slurp is)]
        (do
          (when reset? (.reset is))
          raw-body-str)))))


(defn make-ipn-handler
  ([on-success on-failure] (make-ipn-handler on-success on-failure false true))
  ([on-success on-failure sandbox?] (make-ipn-handler on-success on-failure sandbox? true))
  ([on-success on-failure sandbox? reset?]
   (fn [req]
     (let [body-str (req->raw-body-str req reset?)
           ipn-data (parse-paypal-ipn-string body-str)]
       (do
         (.start (Thread. (fn [] (handle-ipn ipn-data on-success on-failure sandbox?))))
         ;respond to paypal right away, then go and process the ipn-data
         {:status  200
          :headers {"Content-Type" "text/html"}
          :body    ""})))))
