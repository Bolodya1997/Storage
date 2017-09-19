package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.net.Address;

import java.util.*;

class ReplicationPolicy {

    private final static int REPLICATION_DEGREE = 3;

    private final Map<Address, Boolean> addresses = new HashMap<>();
    private final int myNumber;

    ReplicationPolicy(List<Address> addresses, int myNumber) {
        for (Address address : addresses) {
            this.addresses.put(address, true);
        }
        this.myNumber = myNumber;
    }

    void fail(Address address) {
        addresses.put(address, false);
    }

    boolean canSave(String key) {
        List<Integer> numbers = new ArrayList<>();
        for (Map.Entry<Address, Boolean> entry : addresses.entrySet()) {
            if (entry.getValue())
                numbers.add(numbers.size());
            else
                numbers.add(-1);
        }

        int parts = addresses.size();
        double width = Integer.MAX_VALUE;
        double current = key.hashCode();
        int count = 0;
        int pos, number;
        while (count < REPLICATION_DEGREE) {
            width /= parts; //  assumes that parts is always > 0

            pos = (int) Math.round(current / width);
            number = numbers.get(pos);
            if (number == myNumber) {
                return true;
            } else {
                if (number >= 0)
                    ++count;
                numbers.remove(pos);
            }

            current -= pos * width;
            --parts;
        }

        return false;
    }
}
