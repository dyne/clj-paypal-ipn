(defproject org.clojars.dyne/paypal-ipn "0.1.0-SNAPSHOT"
  :description "PayPal IPN handler in Clojure for use with ring and compojure."
  :url "https://github.com/dyne/clj-paypal-ipn"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "3.4.1"]]

  :pedantic? :warn

  :source-paths ["src"]

  :license {:author "Denis Roio"
            :email "jaromil@dyne.org"
            :year 2017
            :key "mit"
            :name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"}

  :deploy-repositories [["releases" {:url :clojars
                                     :creds :gpg}]]

  :profiles {:dev {:plugins [[quickie "0.4.1"]]}})
