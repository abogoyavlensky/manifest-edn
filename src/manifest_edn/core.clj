(ns manifest-edn.core
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [clj-commons.digest :as digest]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [linkboard.routes :as-alias r]))

(def DEFAULT-RESOURCES-DIR "resources")
(def DEFAULT-PUBLIC-DIR "public")
(def DEFAULT-RESOURCES-HASHED-DIR "resources-hashed")
(def DEFAULT-MANIFEST-FILE "manifest.edn")
(def DEFAULT-ASSET-PREFIX "assets")

; Hash assets

(defn- hash-asset-file!
  [{:keys [asset-file target-dir]}]
  (let [content (slurp asset-file)
        content-hash (digest/md5 content)
        asset-file-name (fs/file-name asset-file)
        [asset-file-name-no-ext asset-file-ext] (fs/split-ext asset-file-name)
        asset-file-name-hashed (format "%s.%s.%s" asset-file-name-no-ext content-hash asset-file-ext)
        asset-file-path-hashed (fs/file target-dir asset-file-name-hashed)]

    (when-not (fs/exists? (fs/parent asset-file-path-hashed))
      (fs/create-dirs (fs/parent asset-file-path-hashed)))

    ; create hashed asset file
    (spit asset-file-path-hashed content)
    asset-file-path-hashed))

(defn- fetch-asset!
  "Fetches an asset file from a URL and saves it to resources/public directory.

   Parameters:
   - url: URL to fetch the JavaScript file from
   - filepath: Path to save the file, relative to resources/public

   Returns the path to the saved file."
  [{:keys [url filepath]} target-dir]
  (let [target-filepath (fs/file target-dir filepath)]

    ; Create js directory if it doesn't exist
    (when-not (fs/exists? (fs/parent target-filepath))
      (fs/create-dirs (fs/parent target-filepath)))

    ; Fetch the file and save it
    (println (format "Fetching %s from %s" filepath url))
    (let [response (http/get url)
          content (:body response)]
      (if (= 200 (:status response))
        (do
          ; Save the file
          (spit target-filepath content)
          (println (format "Saved to %s" target-filepath)))
        (throw (ex-info "Failed to fetch JavaScript file"
                        {:url url
                         :status (:status response)
                         :response (:body response)}))))))

(defn fetch-assets!
  [assets-map]
  (let [target-dir (.getPath (fs/file DEFAULT-RESOURCES-DIR DEFAULT-PUBLIC-DIR))]
    (doseq [item assets-map]
      (fetch-asset! item target-dir))))

(defn hash-assets!
  ([]
   (hash-assets! {}))
  ([{:keys [resource-dir public-dir resource-dir-target manifest-file]
     :or {manifest-file DEFAULT-MANIFEST-FILE
          resource-dir DEFAULT-RESOURCES-DIR
          public-dir DEFAULT-PUBLIC-DIR
          resource-dir-target DEFAULT-RESOURCES-HASHED-DIR}}]
   (let [resource-public-path (fs/file resource-dir public-dir)
         asset-files (->> (file-seq resource-public-path)
                          (remove #(fs/directory? %)))
         manifest-map (reduce
                        (fn [manifest file]
                          (let [source-file-relative (->> file
                                                          (fs/components)
                                                          (drop (count (fs/components resource-public-path)))
                                                          (apply fs/file)
                                                          .getPath)
                                target-dir (->> (fs/components file)
                                                (drop (count (fs/components (fs/file resource-dir))))
                                                (concat [(fs/path resource-dir-target)])
                                                (apply fs/file)
                                                (fs/parent))
                                output-file (hash-asset-file! {:asset-file (.getPath file)
                                                               :target-dir target-dir})
                                output-file-relative (->> output-file
                                                          (fs/components)
                                                          (drop (count (fs/components (fs/file resource-dir-target public-dir))))
                                                          (apply fs/file)
                                                          .getPath)]
                            (assoc manifest source-file-relative output-file-relative)))
                        {}
                        asset-files)]
     (spit (fs/file resource-dir-target manifest-file) (pr-str {:assets manifest-map})))))

; Read assets

(def ^:private read-manifest
  (memoize
    (fn [manifest-file]
      (some-> manifest-file
              (io/resource)
              (slurp)
              (edn/read-string)))))

(defn asset
  ([asset-file]
   (asset DEFAULT-ASSET-PREFIX asset-file))
  ([asset-prefix asset-file]
   (let [manifest (read-manifest DEFAULT-MANIFEST-FILE)]
     (format "/%s/%s" asset-prefix (get-in manifest [:assets asset-file] asset-file)))))
