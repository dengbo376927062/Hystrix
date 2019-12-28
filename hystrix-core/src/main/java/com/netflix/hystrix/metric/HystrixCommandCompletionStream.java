/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandKey;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 命令完成流 数据来源 -> HystrixThreadEventStream
 * Per-Command stream of {@link HystrixCommandCompletion}s.  This gets written to by {@link HystrixThreadEventStream}s.
 * Events are emitted synchronously in the same thread that performs the command execution.
 */
public class HystrixCommandCompletionStream implements HystrixEventStream<HystrixCommandCompletion> {
    private final HystrixCommandKey commandKey;

    private final Subject<HystrixCommandCompletion, HystrixCommandCompletion> writeOnlySubject;
    private final Observable<HystrixCommandCompletion> readOnlyStream;

    private static final ConcurrentMap<String, HystrixCommandCompletionStream> streams = new ConcurrentHashMap<String, HystrixCommandCompletionStream>();

    public static HystrixCommandCompletionStream getInstance(HystrixCommandKey commandKey) {
        HystrixCommandCompletionStream initialStream = streams.get(commandKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (HystrixCommandCompletionStream.class) {
                HystrixCommandCompletionStream existingStream = streams.get(commandKey.name());
                if (existingStream == null) {
                    HystrixCommandCompletionStream newStream = new HystrixCommandCompletionStream(commandKey);
                    streams.putIfAbsent(commandKey.name(), newStream);
                    return newStream;
                } else {
                    return existingStream;
                }
            }
        }
    }

    HystrixCommandCompletionStream(final HystrixCommandKey commandKey) {
        this.commandKey = commandKey;
        //命令完成写入主题
        this.writeOnlySubject = new SerializedSubject<HystrixCommandCompletion, HystrixCommandCompletion>(PublishSubject.<HystrixCommandCompletion>create());
        //转换只读数据流（为滑动窗口推送数据）
        this.readOnlyStream = writeOnlySubject.share();
    }

    public static void reset() {
        streams.clear();
    }

    //写入
    public void write(HystrixCommandCompletion event) {
        writeOnlySubject.onNext(event);
    }


    @Override
    public Observable<HystrixCommandCompletion> observe() {
        return readOnlyStream;
    }

    @Override
    public String toString() {
        return "HystrixCommandCompletionStream(" + commandKey.name() + ")";
    }
}
