cmake_minimum_required(VERSION 3.22.1)

project("rasp_detector")

add_library(${CMAKE_PROJECT_NAME} SHARED
    rasp_detector.cpp
    check_env_utils.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
)
