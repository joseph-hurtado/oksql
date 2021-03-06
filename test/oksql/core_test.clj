(ns oksql.core-test
  (:require [clojure.test :refer :all]
            [oksql.core :refer :all]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc])
  (:refer-clojure :exclude [update])
  (:import (java.util UUID Date)
           (java.sql Timestamp)))

(deftest sql-vec-test
  (testing "nil"
    (is (nil? (sql-vec nil nil))))

  (testing "empty values"
    (is (= [""] (sql-vec "" {}))))

  (testing "valid values"
    (is (= ["select * from items where id = ?" 1] (sql-vec "select * from items where id = :id" {:id 1}))))

  (testing "mismatched parameters exception"
    (is (thrown-with-msg? Exception #"Parameter mismatch. Expected :id. Got :item-id"
          (sql-vec "select * from items where id = :id" {:item-id 123}))))

  (testing "ignore more keys just use keys from sql statement"
    (is (= ["select * from items where id = ?" 321] (sql-vec "select * from items where id = :id" {:item-id 123 :id 321})))))

(defn exec [db sql]
  (jdbc/with-db-connection [conn db]
    (with-open [s (.createStatement (jdbc/db-connection conn))]
      (.addBatch s sql)
      (seq (.executeBatch s)))))

(let [conn {:connection-uri "jdbc:postgresql://localhost:5432/postgres"}
      _ (exec conn "drop database if exists oksql_test")
      _ (exec conn "create database oksql_test")
      _ (exec conn "drop table if exists items")
      _ (exec conn "create table items (id serial primary key, name text, created_at timestamp)")
      db {:connection-uri "jdbc:postgresql://localhost:5432/oksql_test"}
      created-at (Timestamp. (.getTime (new Date)))]

  (deftest query-test
    (testing "all"
      (is (= '() (query db :items/all))))

    (testing "fetch"
      (is (= '() (query db :items/fetch {:id 123}))))

    (testing "missing name"
      (is (= '() (query db :items/missing {:id 123}))))

    (testing "insert returning"
      (let [expected {:id 1
                      :name "name"
                      :created-at created-at}]
        (is (= expected (query db :items/insert expected)))))

    (testing "select recently inserted"
      (is (= {:id 1 :name "name" :created-at created-at} (query db :items/fetch {:id 1})))))

  (deftest write-test
    (testing "delete"
      (let [expected {:id 1
                      :name "name"
                      :created-at created-at}]
        (is (= expected (delete db :items :items/where {:id 1})))))

    (testing "insert"
      (let [expected {:id 1
                      :name "hello"
                      :created-at created-at}]
        (is (= expected (insert db :items expected)))))

    (testing "update"
      (let [expected {:id 1
                      :name "world"
                      :created-at created-at}]
        (is (= expected (update db :items {:name "world"} :items/where {:id 1})))))))
