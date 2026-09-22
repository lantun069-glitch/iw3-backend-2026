package ar.edu.iua.iw3.integration.cli2.model.business;


import java.util.Date;
import java.util.List;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.integration.cli2.model.ProductCli2;

public interface IProductCli2Business {
	public List<ProductCli2> listExpired(Date date) throws BusinessException;

}
