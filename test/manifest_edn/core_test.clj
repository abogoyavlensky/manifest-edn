(ns manifest-edn.core-test
  (:require [clojure.test :refer :all]
            [manifest-edn.core :as core]))

(deftest test-sum
  (is (= 3 (core/sum 1 2))))
