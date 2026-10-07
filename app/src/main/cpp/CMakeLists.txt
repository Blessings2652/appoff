cmake_minimum_required(VERSION 3.22.1)

project("deepramcleaner")

add_library(
    ${PROJECT_NAME}
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    ${PROJECT_NAME}
    ${log-lib}
)
