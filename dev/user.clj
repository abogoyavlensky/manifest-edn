(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as test]))

(repl/set-refresh-dirs "dev" "src" "test")

(defn reset
  "Reload changed namespaces."
  []
  (repl/refresh))

(defn run-all-tests
  "Run all tests."
  []
  (reset)
  (test/run-all-tests #"manifest-edn.*-test"))

(comment
  ; Manage the system
  (reset)
  (run-all-tests))
