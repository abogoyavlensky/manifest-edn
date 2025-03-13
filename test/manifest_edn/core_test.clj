(ns manifest-edn.core-test
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [clj-commons.digest :as digest]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [manifest-edn.core :as manifest]))

;; Test utilities and fixtures

(defn- create-temp-dir!
  "Creates a temporary directory for testing and returns its path."
  []
  (let [temp-dir (fs/create-temp-dir)]
    (str temp-dir)))

(defn- create-test-file!
  "Creates a test file with the given content and returns its path."
  [dir filename content]
  (let [file-path (fs/file dir filename)]
    (when-not (fs/exists? (fs/parent file-path))
      (fs/create-dirs (fs/parent file-path)))
    (spit file-path content)
    (.getPath file-path)))

(defn- delete-directory!
  "Deletes a directory and all its contents."
  [dir]
  (when (fs/exists? dir)
    (fs/delete-tree dir)))

(def ^:dynamic *temp-dirs* nil)

(defn with-temp-dirs
  "Fixture that creates temporary directories for testing and cleans them up after."
  [f]
  (let [temp-resource-dir (create-temp-dir!)
        temp-resource-hashed-dir (create-temp-dir!)]
    (try
      (binding [*temp-dirs* {:resource-dir temp-resource-dir
                             :resource-dir-target temp-resource-hashed-dir}]
        (f))
      (finally
        (delete-directory! temp-resource-dir)
        (delete-directory! temp-resource-hashed-dir)))))

(use-fixtures :each with-temp-dirs)

;; Tests for hash-asset-file!

(deftest test-hash-asset-file!
  (testing "hash-asset-file! creates a hashed version of the file"
    (let [temp-dirs {:resource-dir (create-temp-dir!)
                     :resource-dir-target (create-temp-dir!)}
          content "body { color: red; }"
          asset-file (create-test-file! (:resource-dir temp-dirs) "css/styles.css" content)
          target-dir (fs/file (:resource-dir-target temp-dirs) "css")
          expected-hash (digest/md5 content)
          result (#'manifest/hash-asset-file! {:asset-file asset-file
                                               :target-dir (.getPath target-dir)})
          result-filename (fs/file-name result)
          [name-part hash-part ext-part] (str/split result-filename #"\.")]

      (is (fs/exists? result) "Hashed file should exist")
      (is (= "styles" name-part) "Filename base should be preserved")
      (is (= expected-hash hash-part) "Hash should be correct")
      (is (= "css" ext-part) "File extension should be preserved")
      (is (= content (slurp result)) "File content should be preserved")

      ;; Clean up
      (delete-directory! (:resource-dir temp-dirs))
      (delete-directory! (:resource-dir-target temp-dirs)))))

;; Tests for fetch-asset! and fetch-assets!

(deftest test-fetch-asset!
  (testing "fetch-asset! fetches and saves a file from a URL"
    (with-redefs [http/get (fn [_] {:status 200
                                    :body "console.log('test');"})
                  println identity] ; Silence println during tests
      (let [temp-dir (create-temp-dir!)
            url "https://example.com/script.js"
            filepath "js/script.js"
            _result (#'manifest/fetch-asset! {:url url
                                              :filepath filepath} temp-dir)
            expected-path (fs/file temp-dir filepath)]

        (is (fs/exists? expected-path) "File should be saved at the correct path")
        (is (= "console.log('test');" (slurp expected-path)) "File content should match the response")

        ;; Clean up
        (delete-directory! temp-dir)))))

(deftest test-fetch-asset-error
  (testing "fetch-asset! throws an exception when the request fails"
    (with-redefs [http/get (fn [_] {:status 404
                                    :body "Not Found"})
                  println identity] ; Silence println during tests
      (let [temp-dir (create-temp-dir!)
            url "https://example.com/nonexistent.js"
            filepath "js/nonexistent.js"]

        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Failed to fetch JavaScript file"
                              (#'manifest/fetch-asset! {:url url
                                                        :filepath filepath} temp-dir)))

        ;; Clean up
        (delete-directory! temp-dir)))))

(deftest test-fetch-assets!
  (testing "fetch-assets! processes multiple assets"
    (let [called-urls (atom [])]
      (with-redefs [manifest/fetch-asset! (fn [item target-dir]
                                            (swap! called-urls conj [(:url item) (:filepath item) target-dir])
                                            nil)]
        (let [assets [{:url "https://example.com/script1.js"
                       :filepath "js/script1.js"}
                      {:url "https://example.com/script2.js"
                       :filepath "js/script2.js"}]]
          (manifest/fetch-assets! assets)
          (is (= 2 (count @called-urls)) "Should call fetch-asset! for each asset")
          (is (= ["https://example.com/script1.js" "js/script1.js" (str "resources" fs/file-separator "public")]
                 (first @called-urls)) "Should pass correct parameters to fetch-asset!")
          (is (= ["https://example.com/script2.js" "js/script2.js" (str "resources" fs/file-separator "public")]
                 (second @called-urls)) "Should pass correct parameters to fetch-asset!"))))))

;; Tests for hash-assets!

(deftest test-hash-assets!
  (testing "hash-assets! creates hashed versions of all assets and generates a manifest"
    (let [public-dir "public"
          css-content "body { color: blue; }"
          js-content "console.log('hello');"
          _css-file (create-test-file! (:resource-dir *temp-dirs*) (str public-dir "/css/styles.css") css-content)
          _js-file (create-test-file! (:resource-dir *temp-dirs*) (str public-dir "/js/app.js") js-content)
          manifest-file "manifest.edn"]

      ;; Call hash-assets! with our test directories
      (manifest/hash-assets! {:resource-dir (:resource-dir *temp-dirs*)
                              :public-dir public-dir
                              :resource-dir-target (:resource-dir-target *temp-dirs*)
                              :manifest-file manifest-file})

      ;; Check that the manifest file was created
      (let [manifest-path (fs/file (:resource-dir-target *temp-dirs*) manifest-file)]
        (is (fs/exists? manifest-path) "Manifest file should exist")

        ;; Parse the manifest and check its contents
        (let [manifest-content (edn/read-string (slurp manifest-path))
              assets (:assets manifest-content)
              css-path (get assets "css/styles.css")
              js-path (get assets "js/app.js")]

          (is (map? assets) "Manifest should contain an assets map")
          (is (string? css-path) "CSS path should be in the manifest")
          (is (string? js-path) "JS path should be in the manifest")

          ;; Check that the hashed files exist
          (is (fs/exists? (fs/file (:resource-dir-target *temp-dirs*) public-dir css-path))
              "Hashed CSS file should exist")
          (is (fs/exists? (fs/file (:resource-dir-target *temp-dirs*) public-dir js-path))
              "Hashed JS file should exist")

          ;;; Check that the content was preserved
          (is (= css-content (slurp (fs/file (:resource-dir-target *temp-dirs*) public-dir css-path)))
              "CSS content should be preserved")
          (is (= js-content (slurp (fs/file (:resource-dir-target *temp-dirs*) public-dir js-path)))
              "JS content should be preserved"))))))

(deftest test-hash-assets-with-defaults
  (testing "hash-assets! works with default parameters"
    (with-redefs [clojure.core/file-seq (fn [_] [])
                  fs/file (fn [& args] (apply io/file args))
                  spit (fn [_ _] nil)]
      (is (nil? (manifest/hash-assets!)) "Should not throw an exception with default parameters"))))

;; Tests for read-manifest and asset functions

(deftest test-read-manifest
  (testing "read-manifest reads and parses a manifest file"
    (with-redefs [io/resource (fn [path] path)
                  slurp (fn [_] "{:assets {\"css/styles.css\" \"css/styles.abc123.css\"}}")
                  edn/read-string (fn [_] {:assets {"css/styles.css" "css/styles.abc123.css"}})]
      (let [manifest (#'manifest/read-manifest "manifest.edn")]
        (is (map? manifest) "Manifest should be a map")
        (is (= "css/styles.abc123.css" (get-in manifest [:assets "css/styles.css"])) "Should contain the correct asset mapping")))))

(deftest test-asset-function
  (testing "asset function returns the correct path with default prefix"
    (with-redefs [manifest/read-manifest (fn [_] {:assets {"css/styles.css" "css/styles.abc123.css"}})]
      (is (= "/assets/css/styles.abc123.css" (manifest/asset "css/styles.css")) "Should return the correct path with default prefix")))

  (testing "asset function returns the correct path with custom prefix"
    (with-redefs [manifest/read-manifest (fn [_] {:assets {"css/styles.css" "css/styles.abc123.css"}})]
      (is (= "/static/css/styles.abc123.css" (manifest/asset "static" "css/styles.css")) "Should return the correct path with custom prefix")))

  (testing "asset function returns the original path if not found in manifest"
    (with-redefs [manifest/read-manifest (fn [_] {:assets {}})]
      (is (= "/assets/css/styles.css" (manifest/asset "css/styles.css")) "Should return the original path with default prefix")
      (is (= "/static/css/styles.css" (manifest/asset "static" "css/styles.css")) "Should return the original path with custom prefix"))))

(deftest test-asset-function-with-nil-manifest
  (testing "asset function handles nil manifest gracefully"
    (with-redefs [manifest/read-manifest (fn [_] nil)]
      (is (= "/assets/css/styles.css" (manifest/asset "css/styles.css")) "Should return the original path with default prefix")
      (is (= "/custom/css/styles.css" (manifest/asset "custom" "css/styles.css")) "Should return the original path with custom prefix"))))
