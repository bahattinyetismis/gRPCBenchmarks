/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.benchmarks;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.protobufbenchmarker.nano.AddressBook;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.nano.GreeterGrpc;
import io.grpc.examples.helloworld.nano.HelloReply;
import io.grpc.examples.helloworld.nano.HelloRequest;

import java.util.concurrent.TimeUnit;

public class GrpcBenchmarksActivity extends AppCompatActivity {
    private Button mSendButton;
    private Button mBenchmarkButton;
    private EditText mHostEdit;
    private EditText mPortEdit;
    private EditText mMessageEdit;
    private TextView mResultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grpcbenchmarks);
        mSendButton = (Button) findViewById(R.id.send_button);
        mBenchmarkButton = (Button) findViewById(R.id.benchmark_button);
        mHostEdit = (EditText) findViewById(R.id.host_edit_text);
        mPortEdit = (EditText) findViewById(R.id.port_edit_text);
        mMessageEdit = (EditText) findViewById(R.id.message_edit_text);
        mResultText = (TextView) findViewById(R.id.grpc_response_text);
    }

    public void sendMessage(View v) {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(mHostEdit.getWindowToken(), 0);
        mSendButton.setEnabled(false);
        new GrpcTask().execute();
    }

    public void beginBenchmark(View v) {
        mBenchmarkButton.setEnabled(false);
        new GrpcBenchmarkTask().execute();
//        runBenchmarks();

    }

    private class GrpcBenchmarkTask extends AsyncTask<Integer, Void, Void> {
        private String mHost;
        private String mMessage;
        private int mPort;
        private ManagedChannel mChannel;

        @Override
        protected void onPreExecute() {
            mHost = mHostEdit.getText().toString();
            mMessage = mMessageEdit.getText().toString();
            String portStr = mPortEdit.getText().toString();
            mPort = TextUtils.isEmpty(portStr) ? 0 : Integer.valueOf(portStr);
            mResultText.setText("");
        }

        @Override
        protected Void doInBackground(Integer... nums) {
            try {
                mChannel = ManagedChannelBuilder.forAddress(mHost, mPort)
                        .usePlaintext(true)
                        .build();
                final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(mChannel);
                final HelloRequest message = new HelloRequest();
                message.name = mMessage;
                HelloReply reply = stub.sayHello(message);
                long size = message.getSerializedSize() + reply.getSerializedSize();
                benchmark("Sending and recieving hello world greeting (gRPC)",
                        size, new Action() {
                            public void execute() {
                                stub.sayHello(message);
                            }
                });
            } catch (Exception e) {
                System.out.println("Exception! " + e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void nothing) {
            mBenchmarkButton.setEnabled(true);
        }

        private void benchmark(String name, long dataSize, Action task) {
            for (int i = 0; i < 10; ++i) {
                task.execute();
            }

            // Run it progressively more times until we've got a reasonable sample
            int iterations = 1;
            long elapsed = timeAction(task, iterations);
            while (elapsed < 3000) {
                iterations *= 2;
                elapsed = timeAction(task, iterations);
            }

            // Upscale the sample to the target time. Do this in floating point arithmetic
            // to avoid overflow issues.
            iterations = (int) ((30000 / (double) elapsed) * iterations);
            elapsed = timeAction(task, iterations);
            System.out.println(name + ": " + iterations + " iterations in "
                    + (elapsed/1000f) + "s; "
                    + (iterations * dataSize) / (elapsed * 1024 * 1024 / 1000f)
                    + "MB/s");
        }

        private long timeAction(Action task, int iterations) {
            System.gc();
            long start = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                task.execute();
            }
            long end = System.currentTimeMillis();
            return end - start;
        }
    }

    private class GrpcTask extends AsyncTask<Void, Void, String> {
        private String mHost;
        private String mMessage;
        private int mPort;
        private ManagedChannel mChannel;

        @Override
        protected void onPreExecute() {
            mHost = mHostEdit.getText().toString();
            mMessage = mMessageEdit.getText().toString();
            String portStr = mPortEdit.getText().toString();
            mPort = TextUtils.isEmpty(portStr) ? 0 : Integer.valueOf(portStr);
            mResultText.setText("");
        }

        private String sayHello(ManagedChannel channel) {
            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
            HelloRequest message = new HelloRequest();
            message.name = mMessage;
            HelloReply reply = stub.sayHello(message);
            return reply.message;
        }

        @Override
        protected String doInBackground(Void... nothing) {
            try {
                mChannel = ManagedChannelBuilder.forAddress(mHost, mPort)
                    .usePlaintext(true)
                    .build();
                return sayHello(mChannel);
            } catch (Exception e) {
                return "Failed... : " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                mChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mResultText.setText(result);
            mSendButton.setEnabled(true);
        }
    }

    interface Action {
        void execute();
    }
}