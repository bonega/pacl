# Pacl

This library is just wrapping https://github.com/edmund-wagner/junrar and sun's zip utilities.
It provides a more sane clojure interface for working with archives.

The most interesting feature is that it tries to handle both packing
and unpacking with either files or streams.

For example you can read images from
<tt>http://somethingthatisaziparchive</tt>.
Rescale the image and create a new zip with the scaled images.
All that without touching the filesystem.

At the moment only ziparchives supports all features.
Rar-archives can only be read from files.


## Usage:
<tt>compress</tt> and <tt>open-archive</tt> tries to handle anything
that can be coerced into streams.

   (extract-files "https://github.com/bonega/pacl/zipball/master" "thesourceofthislib")

Just extracts all files from something that can be coerced into an inputstream

     (with-open [a (open-archive "somefile.zip")]
           (compress (filter #(str-contains (:filename %) ".jpg") (:entries a)) "newarchive.zip" :method STORED)

Takes all .jpg entries in a zip and creates another archive with no compression

      (compress-files ["data/"] "filename.zip")

compresses all files (or directories) to a new archive.
