set -e

# set paths and names
root=..
module_name=info.kgeorgiy.ja.eliseev.implementor
dependency_module_name=info.kgeorgiy.java.advanced.implementor
compilation_directory=out
library_directory="$root/lib"
manifest_file_name=manifest.txt
class_name=Implementor
jar_file_name="$class_name.jar"

# compile sources
javac --module-path "$library_directory"               \
      --module-source-path ../test                        \
      --module-source-path "$module_name=$root/$module_name" \
      -m "$module_name"              \
      -d "$compilation_directory"

# create jar
echo "Main-Class: $module_name.$class_name" > "$manifest_file_name"
jar cmf "$manifest_file_name" "$jar_file_name" -C "$compilation_directory/$module_name" .
jar uf "$jar_file_name" -C "$compilation_directory/$dependency_module_name" .

# clear files
rm -r "$compilation_directory" "$manifest_file_name" || echo -n ""
