package com.gravityray.rxeventemitter;

import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;


public final class RxEventEmitter<T> {

    private static final class Event<T> {

        private final long id;
        private final T value;
        private final boolean consumed;

        public Event(long id, T value, boolean consumed) {
            this.id = id;
            this.value = value;
            this.consumed = consumed;
        }

        public final long getId() {
            return id;
        }

        public final T getValue() {
            return value;
        }

        public final boolean isConsumed() {
            return consumed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Event<?> that = (Event<?>) o;

            return id == that.id;
        }

        @Override
        public int hashCode() {
            return (int) (id ^ (id >>> 32));
        }
    }

    public static final Object EMPTY = new Object();

    private final Subject<Event<T>> eventSubject;
    private volatile Event<T> lastPushedEvent;

    private final AtomicLong nextId;

    public RxEventEmitter() {
        eventSubject = BehaviorSubject.<Event<T>>create()
                .toSerialized();
        this.nextId = new AtomicLong(0);
    }

    public Observable<T> observe() {
        return eventSubject
                .filter(event -> !event.isConsumed())
                .doOnNext(this::onConsumed)
                .map(Event::getValue);
    }

    public void onNextDistinct(T value) {
        synchronized (this) {
            boolean distinct = lastPushedEvent == null
                    || !equivalent(value, lastPushedEvent.getValue());
            if (distinct) {
                onNext(value);
            }
        }
    }

    private boolean equivalent(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    public void onNext(T value) {
        synchronized (this) {
            Event<T> event = new Event<>(nextId.getAndIncrement(), value, false);
            lastPushedEvent = event;
            eventSubject.onNext(event);
        }
    }

    private void onConsumed(Event<T> event) {
        eventSubject.firstElement()
                .filter(event::equals)
                .subscribe(currentEvent ->
                        eventSubject.onNext(new Event<>(
                                event.getId(),
                                event.getValue(),
                                true)));
    }
}
