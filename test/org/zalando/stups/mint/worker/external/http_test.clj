(ns org.zalando.stups.mint.worker.external.http-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.test-helpers :as u]
            [clj-http.client :as clj-http]
            [org.zalando.stups.mint.worker.external.http :as client]))

(def expected-default-options
  {:socket-timeout 60000
   :conn-timeout 60000})

(deftest test-http-wrapper

  (testing "http options are merged with default"

    (testing "get"
      (let [calls (atom {})]
        (with-redefs [clj-http/get (u/track calls :get)]
          (client/get "localhost.com" {})
          (is
            (=
              (second (first (:get @calls)))
              expected-default-options))))

     (testing "put"
       (let [calls (atom {})]
         (with-redefs [clj-http/put (u/track calls :put)]
           (client/put "localhost.com" {})
           (is
             (=
               (second (first (:put @calls)))
               expected-default-options))))

      (testing "patch"
        (let [calls (atom {})]
          (with-redefs [clj-http/patch (u/track calls :patch)]
            (client/patch "localhost.com" {})
            (is
              (=
                (second (first (:patch @calls)))
                expected-default-options))))


       (testing "post"
         (let [calls (atom {})]
           (with-redefs [clj-http/post (u/track calls :post)]
             (client/post "localhost.com" {})
             (is
               (=
                 (second (first (:post @calls)))
                 expected-default-options))))

        (testing "delete"
          (let [calls (atom {})]
            (with-redefs [clj-http/delete (u/track calls :delete)]
              (client/delete "localhost.com" {})
              (is
                (=
                  (second (first (:delete @calls)))
                  expected-default-options)))))))))))
