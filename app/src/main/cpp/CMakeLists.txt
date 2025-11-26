cmake_minimum_required(VERSION 3.22.1)

project("floodfill")

add_library(${CMAKE_PROJECT_NAME} SHARED
        flood-fill.cpp)

find_library(jnigraphics-lib jnigraphics)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        ${jnigraphics-lib}
        log)