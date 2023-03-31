set -e

# set paths and names
module_name=info.kgeorgiy.ja.eliseev.implementor
test_module_name=info.kgeorgiy.java.advanced.implementor
test_class_name=info.kgeorgiy.java.advanced.implementor.Tester
compilation_directory=out
library_directory=lib

# compile tests and sources
javac --module-path "$library_directory"               \
      --module-source-path test                        \
      --module-source-path "$module_name=$module_name" \
      -m "$test_module_name,$module_name"              \
      -d "$compilation_directory"

# test implement
java --add-modules ALL-MODULE-PATH                  \
     -p "$compilation_directory:$library_directory" \
     -m "$test_module_name/$test_class_name"        \
      generic "$module_name.Implementor"

# test implementJar
java --add-modules ALL-MODULE-PATH                  \
     -p "$compilation_directory:$library_directory" \
     -m "$test_module_name/$test_class_name"        \
      jar-generic "$module_name.Implementor"