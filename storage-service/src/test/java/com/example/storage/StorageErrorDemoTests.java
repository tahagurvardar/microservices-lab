package com.example.storage;

import com.example.storage.controller.StorageController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StorageController.class)
class StorageErrorDemoTests {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void returnsRequestedDemoError(int code) throws Exception {
        mvc.perform(get("/api/storage/simulate-error/{status}", code))
                .andExpect(status().is(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 301, 403, 999})
    void rejectsUnsupportedDemoStatuses(int code) throws Exception {
        mvc.perform(get("/api/storage/simulate-error/{status}", code))
                .andExpect(status().isBadRequest());
    }
}
