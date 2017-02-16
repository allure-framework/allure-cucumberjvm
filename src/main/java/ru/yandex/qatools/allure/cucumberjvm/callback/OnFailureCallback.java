package ru.yandex.qatools.allure.cucumberjvm.callback;

public interface OnFailureCallback<OnFailureCallback, Object> {

    /**
     * Callback call method
     *
     * @return Callback result
     */
    public Object call();
}
