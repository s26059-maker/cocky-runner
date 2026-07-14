package com.cocky.cockyrunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RunnerTest {
    public static void main(String[] args) throws Exception {
        // 방금 터미널에서 친 docker 명령을 Java가 대신 실행
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm", "python:3.11-slim",
                "python", "-c", "print('hello from java')"
        );
        pb.redirectErrorStream(true); // 에러도 같은 출력으로 합침

        Process process = pb.start();          // 실행 시작
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");   // 출력 한 줄씩 모으기
        }

        process.waitFor();                      // 끝날 때까지 기다림
        System.out.println("=== 실행 결과 ===");
        System.out.print(output);
    }
}