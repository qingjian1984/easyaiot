package com.basiclab.iot.device.service.product;

import com.basiclab.iot.device.domain.device.vo.ProductServiceDetailVO;
import com.basiclab.iot.device.domain.device.vo.ProductServiceParamVO;
import com.basiclab.iot.device.domain.product.vo.result.ProductPropertyResultVO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyServicePropertyAdapterTest {

    @Test
    void shouldProjectInputAndOutputParamsWithoutRootPropertyLookup() {
        ProductServiceThingModelHelper helper = mock(ProductServiceThingModelHelper.class);
        ProductServiceParamVO input = ProductServiceParamVO.builder()
                .id(11L).parameterCode("delaySeconds").parameterName("延迟秒数")
                .datatype("INT").min(0).max(300).step(1).required(0).build();
        ProductServiceParamVO output = ProductServiceParamVO.builder()
                .id(12L).parameterCode("accepted").parameterName("是否受理")
                .datatype("BOOL").enumlist("{\"0\":\"否\",\"1\":\"是\"}").required(1).build();
        ProductServiceDetailVO detail = ProductServiceDetailVO.builder()
                .id(7L)
                .inputParams(Collections.singletonList(input))
                .outParams(Collections.singletonList(output))
                .build();
        when(helper.getDetail(7L)).thenReturn(detail);

        LegacyServicePropertyAdapter adapter = new LegacyServicePropertyAdapter(helper);
        List<ProductPropertyResultVO> result = adapter.findAllByServiceId(7L);

        assertEquals(2, result.size());
        assertEquals(7L, result.get(0).getServiceId());
        assertEquals("delaySeconds", result.get(0).getPropertyCode());
        assertEquals("300", result.get(0).getMax());
        assertEquals("accepted", result.get(1).getPropertyCode());
        verify(helper).getDetail(7L);
    }

    @Test
    void shouldFailClosedForMissingService() {
        ProductServiceThingModelHelper helper = mock(ProductServiceThingModelHelper.class);
        when(helper.getDetail(99L)).thenReturn(null);

        assertTrue(new LegacyServicePropertyAdapter(helper).findAllByServiceId(99L).isEmpty());
    }
}
