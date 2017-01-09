(ns org.zalando.stups.mint.worker.external.http-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.test-helpers :as u]
            [clj-http.client :as clj-http]
            [org.zalando.stups.mint.worker.external.http :as client]))

(deftest test-http-wrapper

  (testing "http options are merged with default"

    (testing "get"
      (let [calls (atom {})]
        (with-redefs [clj-http/get (u/track calls :get)]
          (client/get "localhost.com" {})
          (is
            (=
              (second (first (:get @calls)))
              {:socket-timeout 1000
               :conn-timeout 1000}))))

     (testing "put"
       (let [calls (atom {})]
         (with-redefs [clj-http/put (u/track calls :put)]
           (client/put "localhost.com" {})
           (is
             (=
               (second (first (:put @calls)))
               {:socket-timeout 1000
                :conn-timeout 1000}))))

      (testing "patch"
        (let [calls (atom {})]
          (with-redefs [clj-http/patch (u/track calls :patch)]
            (client/patch "localhost.com" {})
            (is
              (=
                (second (first (:patch @calls)))
                {:socket-timeout 1000
                 :conn-timeout 1000}))))


       (testing "post"
         (let [calls (atom {})]
           (with-redefs [clj-http/post (u/track calls :post)]
             (client/post "localhost.com" {})
             (is
               (=
                 (second (first (:post @calls)))
                 {:socket-timeout 1000
                  :conn-timeout 1000}))))

        (testing "delete"
          (let [calls (atom {})]
            (with-redefs [clj-http/delete (u/track calls :delete)]
              (client/delete "localhost.com" {})
              (is
                (=
                  (second (first (:delete @calls)))
                  {:socket-timeout 1000
                   :conn-timeout 1000})))))))))))
