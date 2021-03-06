package com.xfinity.resourceprovider;

import javax.lang.model.element.TypeElement;

class UnnamedPackageException extends Exception {

  public UnnamedPackageException(TypeElement typeElement) {
    super("The package of " + typeElement.getSimpleName() + " is unnamed");
  }
}
